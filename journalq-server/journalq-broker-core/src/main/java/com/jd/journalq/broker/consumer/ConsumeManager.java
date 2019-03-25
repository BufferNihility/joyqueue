package com.jd.journalq.broker.consumer;

import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.broker.archive.ArchiveManager;
import com.jd.journalq.broker.archive.ConsumeArchiveService;
import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.broker.consumer.model.ConsumePartition;
import com.jd.journalq.broker.consumer.model.OwnerShip;
import com.jd.journalq.broker.consumer.model.PullResult;
import com.jd.journalq.broker.monitor.BrokerMonitor;
import com.jd.journalq.common.domain.Consumer.ConsumerPolicy;
import com.jd.journalq.common.domain.PartitionGroup;
import com.jd.journalq.common.domain.TopicName;
import com.jd.journalq.common.event.ConsumerEvent;
import com.jd.journalq.common.event.EventType;
import com.jd.journalq.common.event.MetaEvent;
import com.jd.journalq.common.event.NameServerEvent;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.common.exception.JMQException;
import com.jd.journalq.common.message.MessageLocation;
import com.jd.journalq.common.network.session.Connection;
import com.jd.journalq.common.network.session.Consumer;
import com.jd.journalq.common.network.session.Joint;
import com.jd.journalq.broker.consumer.position.PositionManager;
import com.jd.journalq.broker.consumer.position.model.Position;
import com.jd.journalq.server.retry.api.MessageRetry;
import com.jd.journalq.store.PartitionGroupStore;
import com.jd.journalq.store.StoreService;
import com.jd.journalq.toolkit.concurrent.EventListener;
import com.jd.journalq.toolkit.lang.Close;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.service.Service;
import com.jd.journalq.toolkit.time.SystemClock;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费管理
 * <p>
 * Created by chengzhiliang on 2018/8/16.
 */
public class ConsumeManager extends Service implements Consume, BrokerContextAware {

    private final Logger logger = LoggerFactory.getLogger(ConsumeManager.class);

    // 并行消费处理
    private ConcurrentConsumption concurrentConsumption;
    // 分区消费处理
    private PartitionConsumption partitionConsumption;
    // 消息重试管理
    private MessageRetry messageRetry;
    // 分区管理
    private PartitionManager partitionManager;
    // 集群管理
    private ClusterManager clusterManager;
    // 存储服务
    private StoreService storeService;
    // 位置管理
    private PositionManager positionManager;
    // 延迟消费帮助类
    private FilterMessageSupport filterMessageSupport;
    // 消费者请求消息的次数Map,用于实现每个消费者对每个主题的队列的公平访问,访问策略用轮询实现
    private ConcurrentMap<Joint, AtomicLong> consumeCounter = new ConcurrentHashMap();
    // 分区锁实例
    private PartitionLockInstance lockInstance = new PartitionLockInstance();
    // 监控
    private BrokerMonitor brokerMonitor;
    // 消费归档
    private ArchiveManager archiveManager;
    //
    private BrokerContext brokerContext;

    private ConsumeConfig consumeConfig;

    public ConsumeManager() {
        //do nothing
    }

    public ConsumeManager(ClusterManager clusterManager, StoreService storeService, MessageRetry messageRetry, BrokerMonitor brokerMonitor,
                          ArchiveManager archiveManager, PositionManager positionManager) {
        this.clusterManager = clusterManager;
        this.storeService = storeService;
        this.messageRetry = messageRetry;
        this.brokerMonitor = brokerMonitor;
        this.positionManager = positionManager;
        this.archiveManager = archiveManager;
    }

    @Override
    protected void validate() throws Exception {
        super.validate();

        if (consumeConfig == null) {
            consumeConfig = new ConsumeConfig(brokerContext != null ? brokerContext.getPropertySupplier() : null);
        }
        if (clusterManager == null && brokerContext != null) {
            clusterManager = brokerContext.getClusterManager();
        }
        if (brokerMonitor == null && brokerContext != null) {
            brokerMonitor = brokerContext.getBrokerMonitor();
        }
        if (messageRetry == null && brokerContext != null) {
            messageRetry = brokerContext.getRetryManager();
        }

        if (archiveManager == null && brokerContext != null) {
            archiveManager = brokerContext.getArchiveManager();
        }

        if (storeService ==null && brokerContext!=null){
            storeService = brokerContext.getStoreService();
        }

        Preconditions.checkArgument(clusterManager != null, "cluster manager can not be null.");
        Preconditions.checkArgument(storeService != null, "cluster manager can not be null.");
        if (brokerMonitor == null) {
            logger.warn("broker monitor is null.");
        }
        if (messageRetry == null) {
            logger.warn("message retry is null.");
        }
        if (archiveManager == null) {
            logger.warn("archive manager is null.");
        }
        this.filterMessageSupport = new FilterMessageSupport(clusterManager);
        this.partitionManager = new PartitionManager(clusterManager);
        this.positionManager = new PositionManager(clusterManager, storeService,consumeConfig);
        this.brokerContext.positionManager(positionManager);
        this.partitionConsumption = new PartitionConsumption(clusterManager, storeService, partitionManager, positionManager, messageRetry, filterMessageSupport, archiveManager);
        this.concurrentConsumption = new ConcurrentConsumption(clusterManager, storeService, partitionManager, messageRetry, positionManager, filterMessageSupport, archiveManager);
    }

    @Override
    public void start() throws Exception {
        super.start();
        partitionConsumption.start();
        concurrentConsumption.start();
        positionManager.start();

        registerEventListener(clusterManager);
        logger.info("ConsumeManager is started.");
    }

    @Override
    public void stop() {
        super.stop();
        Close.close(partitionConsumption);
        Close.close(messageRetry);
        Close.close(partitionConsumption);
        Close.close(concurrentConsumption);
        logger.info("ConsumeManager is stopped.");
    }

    /**
     * 注册添加消费者和移除者事件
     */
    private void registerEventListener(ClusterManager clusterManager) {
        clusterManager.addListener(new UpdateConsumeListener());
    }

    @Override
    public PullResult getMessage(Consumer consumer, int count, int ackTimeout) throws JMQException {
        Preconditions.checkArgument(consumer != null, "消费者信息不能为空");

        // 监控开始时间
        long startTime = SystemClock.now();

        ConsumerPolicy consumerPolicy = clusterManager.getConsumerPolicy(TopicName.parse(consumer.getTopic()), consumer.getApp());
        if (count <= 0) {
            // 如果消费条数小于等于0，则获取消费策略的默认值
            Short batchSize = consumerPolicy.getBatchSize();
            count = batchSize;
        }
        if (ackTimeout <= 0) {
            // 如果消息应答时间小于或等于0，则获取消费策略的默认值
            ackTimeout = consumerPolicy.getAckTimeout();
        }
        // 判断是否暂停消费, 返回空
        if (partitionManager.needPause(consumer)) {
            PullResult pullResult = new PullResult(consumer, (short) -1, Collections.emptyList());
            pullResult.setJmqCode(JMQCode.FW_FETCH_TOPIC_MESSAGE_PAUSED);
            return pullResult;
        }

        PullResult pullResult = null;
        // 获取计算，用于均匀消费每个分区
        long accessTimes = getAndIncrement(consumer);
        try {
            //选择消费策略
            switch (choiceConsumeStrategy(consumerPolicy)) {
                case DEFAULT:
                    pullResult = partitionConsumption.getMessage(consumer, count, ackTimeout, accessTimes);
                    break;
                case SEQUENCE:
                    short sequencePartition = getSequencePartition(consumer);
                    pullResult = partitionConsumption.getMessage4Sequence(consumer, sequencePartition, count, ackTimeout);
                    break;
                case CONCURRENT:
                    pullResult = concurrentConsumption.getMessage(consumer, count, ackTimeout, accessTimes);
                    break;
                default:
                    break;
            }
        } catch (JMQException ex) {
            // 连续异常计数
            partitionManager.increaseSerialErr(new OwnerShip(consumer));
            throw ex;
        }
        // 监控逻辑
        if (pullResult.getBuffers().size() > 0) {
            short partition = pullResult.getPartition();
            PartitionGroup partitionGroup = clusterManager.getPartitionGroup(TopicName.parse(consumer.getTopic()), partition);
            int group;
            if (partitionGroup == null) {
                group = -1;
            } else {
                group = partitionGroup.getGroup();
            }
            monitor(pullResult, startTime, consumer, group);
        }
        // 正常返回清除连续出错异常计数
        partitionManager.clearSerialErr(consumer);

        return pullResult;
    }

    /**
     * 根据消费主题和消费应用获取消费策略
     *
     * @param consumerPolicy 消费者策略
     * @return 消费策略
     */
    private ConsumeStrategy choiceConsumeStrategy(ConsumerPolicy consumerPolicy) {
        if (consumerPolicy.getSeq()) {
            return ConsumeStrategy.SEQUENCE;
        } else if (consumerPolicy.isConcurrent()) {
            return ConsumeStrategy.CONCURRENT;
        } else {
            return ConsumeStrategy.DEFAULT;
        }
    }

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.brokerContext = brokerContext;
    }

    /**
     * 消费策略
     */
    private enum ConsumeStrategy {
        // 顺序消费
        SEQUENCE,
        // 并行消费
        CONCURRENT,
        // 默认消费
        DEFAULT
    }

    /**
     * 根据消费者信息拿到对于的顺序消费对于的分区
     *
     * @param consumer
     * @return
     */
    private short getSequencePartition(Consumer consumer) {
        short sequencePartition = 0;
        return sequencePartition;
    }


    /**
     * 访问量计数
     * <br>
     * 计数用于分区负载，并不需要完全精确
     * 这里不保证线程安全
     *
     * @param consumer
     * @return
     */
    private long getAndIncrement(Consumer consumer) {
        Joint joint = new Joint(consumer.getTopic(), consumer.getApp());
        AtomicLong atomicLong = consumeCounter.get(joint);
        if (atomicLong == null) {
            atomicLong = new AtomicLong(0);
            consumeCounter.put(joint, atomicLong);
        }
        return atomicLong.getAndIncrement();
    }

    @Override
    public PullResult getMessage(Consumer consumer, short partition, long index, int count) throws JMQException {
        Preconditions.checkArgument(consumer != null, "消费者信息不能为空");
        Preconditions.checkArgument(partition >= 0, "分区不能小于0");
        Preconditions.checkArgument(index >= 0, "消费序号不能小于0");
        Preconditions.checkArgument(count > 0, "消费条数不能小于或等于0");

        Integer group = partitionManager.getGroupByPartition(TopicName.parse(consumer.getTopic()), partition);
        Preconditions.checkArgument(group != null && group >= 0, "找不到主题[" + consumer.getTopic() + "]" + ",分区[" + partition + "]的分区组");

        long startTime = SystemClock.now();
        try {
            PullResult pullResult = partitionConsumption.getMsgByPartitionAndIndex(consumer, group, partition, index, count);
            // 监控逻辑
            monitor(pullResult, startTime, consumer, group);

            return pullResult;
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
            throw new JMQException(JMQCode.SE_IO_ERROR, e);
        }
    }

    /**
     * 监控逻辑
     *
     * @param pullResult
     * @param startTime
     * @param consumer
     * @param partitionGroup
     */
    private void monitor(PullResult pullResult, long startTime, Consumer consumer, int partitionGroup) {
        if (pullResult != null && CollectionUtils.isNotEmpty(pullResult.getBuffers())) {
            int messageSize = 0;
            for (ByteBuffer buffer : pullResult.getBuffers()) {
                messageSize += (buffer.limit() - buffer.position());
            }
            brokerMonitor.onGetMessage(consumer.getTopic(), consumer.getApp(), partitionGroup, pullResult.getPartition(), pullResult.getBuffers().size(), messageSize, (SystemClock.now() - startTime));
        }
    }

    @Override
    public boolean acknowledge(MessageLocation[] locations, Consumer consumer, Connection connection, boolean isSuccessAck) throws JMQException {
        boolean isSuccess = false;
        if (locations.length < 0) {
            return isSuccess;
        }

        ConsumePartition consumePartition = new ConsumePartition(consumer.getTopic(), consumer.getApp(), locations[0].getPartition());
        ConsumePartition lock = lockInstance.getLockInstance(consumePartition);

        ConsumerPolicy consumerPolicy = clusterManager.getConsumerPolicy(TopicName.parse(consumer.getTopic()), consumer.getApp());
        //选择消费策略
        switch (choiceConsumeStrategy(consumerPolicy)) {
            case DEFAULT:
                synchronized (lock) {
                    isSuccess = partitionConsumption.acknowledge(locations, consumer, isSuccessAck);
                }
                break;
            case SEQUENCE:
                synchronized (lock) {
                    isSuccess = partitionConsumption.acknowledge(locations, consumer, isSuccessAck);
                }
                break;
            case CONCURRENT:
                synchronized (lock) {
                    isSuccess = concurrentConsumption.acknowledge(locations, consumer, isSuccessAck);
                }
                break;
            default:
                break;
        }
        if (isSuccess) {
            // 释放占用
            partitionManager.releasePartition(consumePartition);
            archiveIfNecessary(consumerPolicy, connection, locations);
        }

        return isSuccess;
    }

    public void archiveIfNecessary(ConsumerPolicy policy, Connection connection, MessageLocation[] messageLocations) {
        try {
            ConsumeArchiveService archiveService;
            if (policy.getArchive() == null || !policy.getArchive() || archiveManager == null || (archiveService = archiveManager.getConsumeArchiveService()) == null) {
                return;
            }
            archiveService.appendConsumeLog(connection, messageLocations);

        } catch (Throwable th) {
            logger.warn(String.format("archive message consume error,locations: %s ", messageLocations), th);
        }

    }

    @Override
    public boolean hasFreePartition(Consumer consumer) {
        return partitionManager.hasFreePartition(consumer);
    }

    @Override
    public long getPullIndex(Consumer consumer, short partition) {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");

        Position position = positionManager.getPosition(TopicName.parse(consumer.getTopic()), consumer.getApp(), partition);
        if (position == null) {
            return -1;
        }

        return position.getPullCurIndex();
    }

    @Override
    public void setPullIndex(Consumer consumer, short partition, long index) throws JMQException {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");
        Preconditions.checkArgument(index > -1, "index can not be negative.");

        positionManager.updateLastMsgPullIndex(TopicName.parse(topic), app, partition, index);
    }

    @Override
    public long getAckIndex(Consumer consumer, short partition) {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");

        // 判断topic和partition是否存在
        Integer partitionGroupId = clusterManager.getPartitionGroupId(TopicName.parse(topic), partition);
        if (partitionGroupId == null) {
            return -1;
        }

        Position position = positionManager.getPosition(TopicName.parse(topic), app, partition);
        if (position == null) {
            return -1;
        }

        return position.getAckCurIndex();
    }

    @Override
    public long getStartIndex(Consumer consumer, short partition) {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");

        // 判断topic和partition是否存在
        Integer partitionGroupId = clusterManager.getPartitionGroupId(TopicName.parse(topic), partition);
        if (partitionGroupId == null) {
            return -1;
        }

        Position position = positionManager.getPosition(TopicName.parse(topic), app, partition);
        if (position == null) {
            return -1;
        }

        return position.getAckStartIndex();
    }

    @Override
    public void setAckIndex(Consumer consumer, short partition, long index) {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");
        Preconditions.checkArgument(index > -1, "index can not be negative.");

        positionManager.updateLastMsgAckIndex(TopicName.parse(topic), app, partition, index);
    }

    @Override
    public void setStartAckIndex(Consumer consumer, short partition, long index) {
        String topic = consumer.getTopic();
        String app = consumer.getApp();
        Preconditions.checkArgument(topic != null, "topic can not be null.");
        Preconditions.checkArgument(app != null, "app can not be null.");
//        Preconditions.checkArgument(index > -1, "index can not negative.");

        positionManager.updateStartMsgAckIndex(TopicName.parse(topic), app, partition, index);
    }

    @Override
    public boolean resetPullIndex(String topic, String app) throws JMQException {
        // 获取当前broker上master角色的partition集合
        List<Short> masterPartitionList = clusterManager.getMasterPartitionList(TopicName.parse(topic));
        // 遍历partition集合，重置消费拉取位置
        int successCount = 0;
        for (short partition : masterPartitionList) {
            long lastMsgPullIndex = positionManager.getLastMsgPullIndex(TopicName.parse(topic), app, partition);
            boolean isSuccess = positionManager.updateLastMsgPullIndex(TopicName.parse(topic), app, partition, lastMsgPullIndex);
            if (isSuccess) {
                successCount++;
            }
        }
        // 分别更新每个partition位置，都成功则返回成功，否则失败
        return masterPartitionList.size() == successCount;
    }

    @Override
    public boolean setConsumeInfo(String consumeInfoJson) {
        if (StringUtils.isEmpty(consumeInfoJson)) {
            return false;
        }
        return positionManager.setConsumePosition(consumeInfoJson);
    }

    @Override
    public String getConsumeInfoByGroup(TopicName topic, String app, int partitionGroup) {
        List<PartitionGroup> partitionGroupList = clusterManager.getPartitionGroup(topic);
        if (CollectionUtils.isEmpty(partitionGroupList)) {
            return null;
        }
        return positionManager.getConsumePosition(topic, app, partitionGroup);
    }

    @Override
    public long getMinIndex(Consumer consumer, short partition) {
        String topic = consumer.getTopic();
        Integer partitionGroupId = clusterManager.getPartitionGroupId(TopicName.parse(topic), partition);
        PartitionGroupStore store = storeService.getStore(topic, partitionGroupId);
        return store.getLeftIndex(partition);
    }

    @Override
    public long getMaxIndex(Consumer consumer, short partition) {
        String topic = consumer.getTopic();
        Integer partitionGroupId = clusterManager.getPartitionGroupId(TopicName.parse(topic), partition);
        PartitionGroupStore store = storeService.getStore(topic, partitionGroupId);
        return store.getRightIndex(partition);
    }

    /**
     * 更新消费配置事件
     */
    class UpdateConsumeListener implements EventListener {

        @Override
        public void onEvent(Object event) {
            if (((MetaEvent) event).getEventType() == EventType.UPDATE_CONSUMER) {
                NameServerEvent nameServerEvent = (NameServerEvent) event;
                logger.info("listen update consume event.");
                ConsumerEvent updateConsumerEvent = (ConsumerEvent) nameServerEvent.getMetaEvent();
                try {
                    ConsumerPolicy consumerPolicy = clusterManager.getConsumerPolicy(updateConsumerEvent.getTopic(), updateConsumerEvent.getApp());
                    Integer readRetryProbability = consumerPolicy.getReadRetryProbability();
                    partitionManager.resetRetryProbability(readRetryProbability);
                } catch (JMQException e) {
                    logger.error("listen update consume event error.", e);
                }
            }
        }
    }

}