package com.jd.journalq.client.internal.producer.support;

import com.google.common.collect.Lists;
import com.jd.journalq.client.internal.cluster.ClusterManager;
import com.jd.journalq.client.internal.exception.ClientException;
import com.jd.journalq.client.internal.metadata.domain.PartitionMetadata;
import com.jd.journalq.client.internal.metadata.domain.TopicMetadata;
import com.jd.journalq.client.internal.nameserver.NameServerConfig;
import com.jd.journalq.client.internal.nameserver.helper.NameServerHelper;
import com.jd.journalq.client.internal.producer.MessageSender;
import com.jd.journalq.client.internal.producer.PartitionSelector;
import com.jd.journalq.client.internal.producer.callback.AsyncBatchProduceCallback;
import com.jd.journalq.client.internal.producer.callback.AsyncBatchProduceCallbackAdapter;
import com.jd.journalq.client.internal.producer.callback.AsyncBatchSendCallback;
import com.jd.journalq.client.internal.producer.callback.AsyncProduceCallback;
import com.jd.journalq.client.internal.producer.checker.ProduceMessageChecker;
import com.jd.journalq.client.internal.producer.config.ProducerConfig;
import com.jd.journalq.client.internal.producer.domain.ProduceMessage;
import com.jd.journalq.client.internal.producer.domain.SendBatchResultData;
import com.jd.journalq.client.internal.producer.domain.SendResult;
import com.jd.journalq.client.internal.producer.exception.NeedRetryException;
import com.jd.journalq.client.internal.producer.exception.ProducerException;
import com.jd.journalq.client.internal.producer.helper.ProducerHelper;
import com.jd.journalq.client.internal.producer.interceptor.ProduceContext;
import com.jd.journalq.client.internal.producer.interceptor.ProducerInterceptor;
import com.jd.journalq.client.internal.producer.interceptor.ProducerInterceptorManager;
import com.jd.journalq.client.internal.producer.interceptor.ProducerInvocation;
import com.jd.journalq.client.internal.producer.interceptor.ProducerInvoker;
import com.jd.journalq.client.internal.producer.transport.ProducerClient;
import com.jd.journalq.client.internal.producer.transport.ProducerClientManager;
import com.jd.journalq.client.internal.transport.ClientState;
import com.jd.journalq.common.domain.Producer;
import com.jd.journalq.common.domain.QosLevel;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.common.network.domain.BrokerNode;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.retry.RetryPolicy;
import com.jd.journalq.toolkit.service.Service;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MessageProducerInner
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/1/4
 */
public class MessageProducerInner extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(MessageProducerInner.class);

    private ProducerConfig config;
    private NameServerConfig nameServerConfig;
    private MessageSender messageSender;
    private ClusterManager clusterManager;
    private ProducerClientManager producerClientManager;
    private PartitionSelectorManager partitionSelectorManager;
    private ProducerInterceptorManager producerInterceptorManager;

    public MessageProducerInner(ProducerConfig config, NameServerConfig nameServerConfig, MessageSender messageSender, ClusterManager clusterManager, ProducerClientManager producerClientManager) {
        this(config, nameServerConfig, messageSender, clusterManager, producerClientManager, new ProducerInterceptorManager());
    }

    public MessageProducerInner(ProducerConfig config, NameServerConfig nameServerConfig, MessageSender messageSender, ClusterManager clusterManager, ProducerClientManager producerClientManager, ProducerInterceptorManager producerInterceptorManager) {
        this.config = config;
        this.nameServerConfig = nameServerConfig;
        this.messageSender = messageSender;
        this.clusterManager = clusterManager;
        this.producerClientManager = producerClientManager;
        this.producerInterceptorManager = producerInterceptorManager;
    }

    @Override
    protected void validate() throws Exception {
        partitionSelectorManager = new PartitionSelectorManager();
    }

    public synchronized void addInterceptor(ProducerInterceptor interceptor) {
        producerInterceptorManager.addInterceptor(interceptor);
    }

    public synchronized void removeInterceptor(ProducerInterceptor interceptor) {
        producerInterceptorManager.removeInterceptor(interceptor);
    }

    public SendResult send(ProduceMessage message, String txId, long timeout, TimeUnit timeoutUnit, boolean isOneway, boolean failover, AsyncProduceCallback callback) {
        AsyncBatchProduceCallback asyncBatchProduceCallback = (callback == null ? null : new AsyncBatchProduceCallbackAdapter(callback));
        List<SendResult> sendResults = batchSend(Lists.newArrayList(message), txId, timeout, timeoutUnit, isOneway, failover, asyncBatchProduceCallback);
        if (CollectionUtils.isEmpty(sendResults)) {
            return null;
        }
        return sendResults.get(0);
    }

    public List<SendResult> batchSend(List<ProduceMessage> messages, String txId, long timeout, TimeUnit timeoutUnit, boolean isOneway, boolean failover, AsyncBatchProduceCallback callback) {
        Preconditions.checkArgument(timeoutUnit != null, "timeoutUnit not null");
        ProduceMessageChecker.checkMessages(messages, config);

        return doBatchSend(messages, txId, timeout, timeoutUnit, isOneway, failover, callback);
    }

    public List<SendResult> doBatchSend(List<ProduceMessage> messages, String txId, long timeout, TimeUnit timeoutUnit, boolean isOneway, boolean failover, AsyncBatchProduceCallback callback) {
        TopicMetadata topicMetadata = checkTopicMetadata(messages.get(0).getTopic());
        List<BrokerNode> brokers = getRegionBrokers(topicMetadata);
        brokers = filterNotAvailableBrokers(brokers);
        List<PartitionMetadata> partitions = getBrokerPartitions(topicMetadata, brokers);
        PartitionMetadata partition = dispatchPartitions(messages, topicMetadata, partitions, null);

        return doBatchSend(messages, topicMetadata, partition, partitions, txId, timeout, timeoutUnit, isOneway, failover, callback);
    }

    public List<SendResult> doBatchSend(final List<ProduceMessage> messages, final TopicMetadata topicMetadata, final PartitionMetadata partition, final List<PartitionMetadata> partitions,
                                        final String txId, final long timeout, final TimeUnit timeoutUnit, final boolean isOneway, final boolean failover, final AsyncBatchProduceCallback callback) {

        try {
            return new ProducerInvocation(config, nameServerConfig, topicMetadata,messages, producerInterceptorManager, new ProducerInvoker() {
                @Override
                public List<SendResult> invoke(ProduceContext context) {
                    return doBatchSendInternal(messages, topicMetadata, partition, partitions, txId, timeout, timeoutUnit, isOneway, failover, callback);
                }

                @Override
                public List<SendResult> reject(ProduceContext context) {
                    throw new ProducerException("reject send", JMQCode.CN_UNKNOWN_ERROR.getCode());
                }
            }).invoke();
        } catch (Exception e) {
            if (e instanceof ProducerException) {
                throw (ProducerException) e;
            } else {
                throw new ProducerException(e);
            }
        }
    }

    protected List<SendResult> doBatchSendInternal(List<ProduceMessage> messages, TopicMetadata topicMetadata, PartitionMetadata partition, List<PartitionMetadata> partitions,
                                                   String txId, long timeout, TimeUnit timeoutUnit, boolean isOneway, boolean failover, AsyncBatchProduceCallback callback) {

        List<PartitionMetadata> partitionBlackList = null;
        Producer.ProducerPolicy producerPolicy = topicMetadata.getProducerPolicy();

        String topic = topicMetadata.getTopic();
        String app = config.getApp();
        long produceTimeout = (config.getProduceTimeout() == ProducerConfig.NONE_PRODUCE_TIMEOUT ? producerPolicy.getTimeOut() : config.getProduceTimeout());
        timeout = timeoutUnit.toMillis(timeout);

        RetryPolicy retryPolicy = config.getRetryPolicy();
        int retryTimes = 0;
        int retryLimit = (failover ? retryPolicy.getMaxRetrys() : 0);
        Exception lastException = null;
        List<SendResult> result = null;

        for (int i = 0; i <= retryLimit; i++) {
            if (retryTimes != 0 && failover) {
                if (partitionBlackList == null) {
                    partitionBlackList = Lists.newLinkedList();
                }
                partitionBlackList.add(partition);
                ProducerHelper.clearPartitions(messages);
                partition = dispatchPartitions(messages, topicMetadata, partitions, partitionBlackList);
            }

            try {
                result = doBatchSend(partition.getLeader(), topic, app, messages, txId, config.getQosLevel(), produceTimeout, timeout, isOneway, callback);
                break;
            } catch (NeedRetryException e) {
                lastException = new ProducerException(e.getMessage(), e.getCode(), e.getCause());
                retryTimes++;
                logger.debug("send message exception, topic: {}, app:{} messages: {}", topic, app, messages, e);
            } catch (ClientException e) {
                lastException = e;
                retryTimes++;
                logger.debug("send message exception, topic: {}, app:{} messages: {}", topic, app, messages, e);
            } catch (Exception e) {
                logger.error("send message exception, topic: {}, app:{} messages: {}", topic, app, messages, e);
                new ProducerException(lastException);
            }
        }

        if (retryTimes > retryPolicy.getMaxRetrys()) {
            if (lastException instanceof ProducerException) {
                throw (ProducerException) lastException;
            }
            throw new ProducerException(lastException);
        }
        if (retryTimes != 0) {
            logger.warn("send message success, retry {} times, topic: {}, app: {}, partitions: {}, error: {}", retryTimes, topic, app, partitionBlackList, lastException.getMessage());
        }
        return result;
    }

    protected List<SendResult> doBatchSend(BrokerNode brokerNode, String topic, String app, List<ProduceMessage> messages, String txId, QosLevel qosLevel,
                                           long produceTimeout, long timeout, boolean isOneway, final AsyncBatchProduceCallback callback) {

        if (logger.isDebugEnabled()) {
            logger.debug("batch send message, broker: {}, topic: {}, app: {}, messages: {}, txId: {}, qosLevel: {}", brokerNode, topic, app, messages, txId, qosLevel);
        }

        if (isOneway) {
            messageSender.batchSendOneway(brokerNode, topic, app, txId, messages, qosLevel, produceTimeout, timeout);
            return null;
        }

        if (callback == null) {
            SendBatchResultData sendBatchResultData = messageSender.batchSend(brokerNode, topic, app, txId, messages, qosLevel, produceTimeout, timeout);
            return handleSendBatchResultData(topic, app, sendBatchResultData);
        } else {
            messageSender.batchSendAsync(brokerNode, topic, app, txId, messages, qosLevel, produceTimeout, timeout, new AsyncBatchSendCallback() {
                @Override
                public void onSuccess(List<ProduceMessage> messages, SendBatchResultData result) {
                    if (result.getCode().equals(JMQCode.SUCCESS)) {
                        callback.onSuccess(messages, result.getResult());
                    } else {
                        callback.onException(messages, new ProducerException(result.getCode().getMessage(), result.getCode().getCode()));
                    }
                }

                @Override
                public void onException(List<ProduceMessage> messages, Throwable cause) {
                    callback.onException(messages, cause);
                }
            });
            return null;
        }
    }

    protected List<SendResult> handleSendBatchResultData(String topic, String app, SendBatchResultData sendBatchResultData) {
        if (sendBatchResultData == null) {
            throw new ProducerException(JMQCode.CN_UNKNOWN_ERROR.getMessage(), JMQCode.CN_UNKNOWN_ERROR.getCode());
        }

        JMQCode code = sendBatchResultData.getCode();
        if (code.equals(JMQCode.SUCCESS)) {
            return sendBatchResultData.getResult();
        }

        // TODO 临时日志
        logger.warn("send message error, topic: {}, code: {}, error: {}", topic, code, code.getMessage());

        switch (code) {
            case CN_NO_PERMISSION:
            case CN_SERVICE_NOT_AVAILABLE:
            case FW_PRODUCE_MESSAGE_BROKER_NOT_LEADER: {
                // 尝试更新元数据
                logger.debug("send message error, no permission, topic: {}", topic);
                clusterManager.tryUpdateTopicMetadata(topic, app);
                break;
            }
            case FW_PUT_MESSAGE_TOPIC_NOT_WRITE: {
                logger.debug("send message error, not write, topic: {}", topic);
                break;
            }
            case FW_TOPIC_NOT_EXIST: {
                logger.debug("send message error, topic not exist, topic: {}", topic);
                throw new ProducerException(code.getMessage(), code.getCode());
            }
            default: {
                logger.error("send message error, topic: {}, code: {}, error: {}", topic, code, code.getMessage());
                throw new NeedRetryException(code.getMessage(), code.getCode());
            }
        }
        throw new NeedRetryException(code.getMessage(), code.getCode());
    }

    public TopicMetadata checkTopicMetadata(String topic) {
        TopicMetadata topicMetadata = clusterManager.fetchTopicMetadata(getTopicFullName(topic), config.getApp());
        if (topicMetadata == null) {
            throw new ProducerException(String.format("topic %s is not exist", topic), JMQCode.FW_TOPIC_NOT_EXIST.getCode());
        }
        if (topicMetadata.getProducerPolicy() == null) {
            throw new ProducerException(String.format("topic %s producer %s is not exist", topic, nameServerConfig.getApp()), JMQCode.FW_PRODUCER_NOT_EXISTS.getCode());
        }
        return topicMetadata;
    }

    public String getTopicFullName(String topic) {
        return NameServerHelper.getTopicFullName(topic, nameServerConfig);
    }

    public List<BrokerNode> getRegionBrokers(TopicMetadata topicMetadata) {
        if (topicMetadata.getProducerPolicy().getNearby()) {
            return topicMetadata.getNearbyBrokers();
        } else {
            return topicMetadata.getBrokers();
        }
    }

    public List<BrokerNode> filterNotAvailableBrokers(List<BrokerNode> brokerNodes) {
        if (CollectionUtils.isEmpty(brokerNodes)) {
            return brokerNodes;
        }
        List<BrokerNode> newBrokerNodes = null;
        for (BrokerNode brokerNode : brokerNodes) {
            ProducerClient client = producerClientManager.tryGetClient(brokerNode);
            if (client == null || client.getState().equals(ClientState.CONNECTED)) {
                continue;
            }
            if (newBrokerNodes == null) {
                newBrokerNodes = Lists.newArrayList(brokerNodes);
            }
            newBrokerNodes.remove(brokerNode);
        }
        if (newBrokerNodes == null) {
            return brokerNodes;
        }
        return newBrokerNodes;
    }

    public List<PartitionMetadata> getBrokerPartitions(TopicMetadata topicMetadata, List<BrokerNode> brokerNodes) {
        if (topicMetadata.getBrokers().equals(brokerNodes)) {
            return topicMetadata.getPartitions();
        }
        List<PartitionMetadata> result = Lists.newArrayListWithCapacity(topicMetadata.getPartitions().size());
        for (BrokerNode brokerNode : brokerNodes) {
            List<PartitionMetadata> brokerPartitions = topicMetadata.getBrokerPartitions(brokerNode.getId());
            if (brokerPartitions != null) {
                result.addAll(brokerPartitions);
            }
        }
        return result;
    }

    public PartitionMetadata dispatchPartitions(List<ProduceMessage> messages, TopicMetadata topicMetadata, List<PartitionMetadata> partitions) {
        return dispatchPartitions(messages, topicMetadata, partitions, null);
    }

    public PartitionMetadata dispatchPartitions(List<ProduceMessage> messages, TopicMetadata topicMetadata, List<PartitionMetadata> partitions, List<PartitionMetadata> partitionBlackList) {
        if (CollectionUtils.isEmpty(partitions)) {
            throw new ProducerException(String.format("no partitions available, topic: %s, messages: %s", topicMetadata.getTopic(), messages), JMQCode.FW_TOPIC_NO_PARTITIONGROUP.getCode());
        }
        if (partitionBlackList != null) {
            partitions = ProducerHelper.filterBlackList(partitions, partitionBlackList);
        }
        if (CollectionUtils.isEmpty(partitions)) {
            throw new ProducerException(String.format("no partitions available, topic: %s, messages: %s", topicMetadata.getTopic(), messages), JMQCode.FW_TOPIC_NO_PARTITIONGROUP.getCode());
        }
        PartitionSelector partitionSelector = partitionSelectorManager.getPartitionSelector(topicMetadata.getTopic(), config.getSelectorType());
        PartitionMetadata partition = ProducerHelper.dispatchPartitions(messages, topicMetadata, partitions, partitionSelector);
        if (partition == null || partition.getLeader() == null) {
            throw new ProducerException(String.format("partition is not available, topic: %s, messages: %s", topicMetadata.getTopic(), messages), JMQCode.FW_TOPIC_NO_PARTITIONGROUP.getCode());
        }
        ProducerHelper.setPartitions(messages, partition.getId());
        return partition;
    }
}