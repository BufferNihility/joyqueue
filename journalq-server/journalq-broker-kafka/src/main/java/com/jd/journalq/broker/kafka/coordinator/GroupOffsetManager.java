package com.jd.journalq.broker.kafka.coordinator;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.broker.index.command.ConsumeIndexQueryRequest;
import com.jd.journalq.broker.index.command.ConsumeIndexQueryResponse;
import com.jd.journalq.broker.index.command.ConsumeIndexStoreRequest;
import com.jd.journalq.broker.index.command.ConsumeIndexStoreResponse;
import com.jd.journalq.broker.index.model.IndexAndMetadata;
import com.jd.journalq.broker.index.model.IndexMetadataAndError;
import com.jd.journalq.broker.kafka.KafkaErrorCode;
import com.jd.journalq.broker.kafka.config.KafkaConfig;
import com.jd.journalq.broker.kafka.model.OffsetAndMetadata;
import com.jd.journalq.broker.kafka.model.OffsetMetadataAndError;
import com.jd.journalq.common.domain.Broker;
import com.jd.journalq.common.domain.TopicConfig;
import com.jd.journalq.common.domain.TopicName;
import com.jd.journalq.common.network.command.CommandType;
import com.jd.journalq.common.network.transport.Transport;
import com.jd.journalq.common.network.transport.codec.JMQHeader;
import com.jd.journalq.common.network.transport.command.Command;
import com.jd.journalq.common.network.transport.command.CommandCallback;
import com.jd.journalq.common.network.transport.command.Direction;
import com.jd.journalq.toolkit.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * GroupOffsetManager
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/7
 */
// TODO 优化代码
public class GroupOffsetManager extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(GroupOffsetManager.class);

    private KafkaConfig config;
    private ClusterManager clusterManager;
    private GroupOffsetSyncSessionManager groupOffsetSyncSessionManager;

    public GroupOffsetManager(KafkaConfig config, ClusterManager clusterManager) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.groupOffsetSyncSessionManager = new GroupOffsetSyncSessionManager(config);
    }

    @Override
    protected void doStart() throws Exception {
        groupOffsetSyncSessionManager.start();
    }

    @Override
    public void doStop() {
        if (groupOffsetSyncSessionManager != null) {
            groupOffsetSyncSessionManager.stop();
        }
    }

    public Table<String, Integer, OffsetMetadataAndError> getOffsets(String groupId, HashMultimap<String /** topic **/, Integer /** partition **/> topicAndPartitions) {
        Table<Broker, String, Set<Integer>> brokerTopicPartitionTable = splitPartitionByBroker(topicAndPartitions);
        Table<String, Integer, OffsetMetadataAndError> result = HashBasedTable.create();
        CountDownLatch latch = new CountDownLatch(brokerTopicPartitionTable.size());

        for (String topic : topicAndPartitions.keys()) {
            for (int partition : topicAndPartitions.get(topic)) {
                result.put(topic, partition, OffsetMetadataAndError.OFFSET_SYNC_FAIL);
            }
        }

        for (Broker broker : brokerTopicPartitionTable.rowKeySet()) {
            Map<String, Set<Integer>> topicPartitionMap = brokerTopicPartitionTable.row(broker);

            try {
                Transport transport = groupOffsetSyncSessionManager.getOrCreateTransport(broker);
                ConsumeIndexQueryRequest indexQueryRequest = new ConsumeIndexQueryRequest(groupId, topicPartitionMap);
                JMQHeader header = new JMQHeader(Direction.REQUEST, CommandType.CONSUME_INDEX_QUERY_REQUEST);
                Command request = new Command(header, indexQueryRequest);

                transport.async(request, config.getCoordinatorOffsetSyncTimeout(), new CommandCallback() {
                    @Override
                    public void onSuccess(Command request, Command response) {
                        ConsumeIndexQueryResponse payload = (ConsumeIndexQueryResponse) response.getPayload();
                        for (Map.Entry<String, Map<Integer, IndexMetadataAndError>> topicEntry : payload.getTopicPartitionIndex().entrySet()) {
                            String topic = topicEntry.getKey();
                            for (Map.Entry<Integer, IndexMetadataAndError> partitionEntry : topicEntry.getValue().entrySet()) {
                                IndexMetadataAndError indexMetadataAndError = partitionEntry.getValue();
                                result.put(topic, partitionEntry.getKey(),
                                        new OffsetMetadataAndError(indexMetadataAndError.getIndex(), indexMetadataAndError.getMetadata(), KafkaErrorCode.jmqCodeFor(indexMetadataAndError.getError())));
                            }
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onException(Command request, Throwable cause) {
                        logger.error("get offset failed, async transport exception, leader: {id: {}, ip: {}, port: {}}",
                                broker.getId(), broker.getIp(), broker.getBackEndPort(), cause);
                        latch.countDown();
                    }
                });
            } catch (Throwable t) {
                logger.error("get offset failed, async transport exception, leader: {id: {}, ip: {}, port: {}}",
                        broker.getId(), broker.getIp(), broker.getBackEndPort(), t);
                latch.countDown();
            }
        }

        try {
            latch.await(config.getCoordinatorOffsetSyncTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("get offset latch await exception, groupId: {}, topicAndPartitions: {}", groupId, topicAndPartitions, e);
        }

        return result;
    }

    public Table<String, Integer, OffsetMetadataAndError> saveOffsets(String groupId, Table<String /** topic **/, Integer /** partition **/, OffsetAndMetadata> offsetAndMetadataTable) {
        Table<Broker, String, Set<Integer>> brokerTopicPartitionTable = splitPartitionByBroker(offsetAndMetadataTable);
        Table<String, Integer, OffsetMetadataAndError> result = HashBasedTable.create();
        CountDownLatch latch = new CountDownLatch(brokerTopicPartitionTable.size());

        for (String topic : offsetAndMetadataTable.rowKeySet()) {
            for (Map.Entry<Integer, OffsetAndMetadata> partitionEntry : offsetAndMetadataTable.row(topic).entrySet()) {
                result.put(topic, partitionEntry.getKey(), OffsetMetadataAndError.OFFSET_SYNC_FAIL);
            }
        }

        for (Broker broker : brokerTopicPartitionTable.rowKeySet()) {
            Map<String, Map<Integer, IndexAndMetadata>> indexAndMetadataMap = buildSaveOffsetParam(brokerTopicPartitionTable.row(broker), offsetAndMetadataTable);

            try {
                Transport transport = groupOffsetSyncSessionManager.getOrCreateTransport(broker);
                ConsumeIndexStoreRequest indexStoreRequest = new ConsumeIndexStoreRequest(groupId, indexAndMetadataMap);
                JMQHeader header = new JMQHeader(Direction.REQUEST, CommandType.CONSUME_INDEX_STORE_REQUEST);
                Command request = new Command(header, indexStoreRequest);

                transport.async(request, config.getCoordinatorOffsetSyncTimeout(), new CommandCallback() {
                    @Override
                    public void onSuccess(Command request, Command response) {
                        ConsumeIndexStoreResponse payload = (ConsumeIndexStoreResponse) response.getPayload();
                        for (Map.Entry<String, Map<Integer, Short>> topicEntry : payload.getIndexStoreStatus().entrySet()) {
                            String topic = topicEntry.getKey();
                            for (Map.Entry<Integer, Short> partitionEntry : topicEntry.getValue().entrySet()) {
                                result.put(topic, partitionEntry.getKey(),
                                        new OffsetMetadataAndError(OffsetAndMetadata.INVALID_OFFSET, OffsetAndMetadata.NO_METADATA, KafkaErrorCode.jmqCodeFor(partitionEntry.getValue())));
                            }
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onException(Command request, Throwable cause) {
                        logger.error("save offset failed, async transport exception, leader: {id: {}, ip: {}, port: {}}",
                                broker.getId(), broker.getIp(), broker.getBackEndPort(), cause);
                        latch.countDown();
                    }
                });
            } catch (Throwable t) {
                logger.error("save offset failed, async transport exception, leader: {id: {}, ip: {}, port: {}}",
                        broker.getId(), broker.getIp(), broker.getBackEndPort(), t);
                latch.countDown();
            }
        }

        try {
            latch.await(config.getCoordinatorOffsetSyncTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("save offset latch await exception, groupId: {}, topicAndPartitions: {}", groupId, offsetAndMetadataTable, e);
        }

        return result;
    }

    protected Map<String, Map<Integer, IndexAndMetadata>> buildSaveOffsetParam(Map<String, Set<Integer>> topicAndPartitionMap, Table<String, Integer, OffsetAndMetadata> offsetAndMetadataTable) {
        Map<String, Map<Integer, IndexAndMetadata>> indexAndMetadataMap = Maps.newHashMap();
        for (Map.Entry<String, Set<Integer>> topicEntry : topicAndPartitionMap.entrySet()) {
            String topic = topicEntry.getKey();
            for (Integer partition : topicEntry.getValue()) {
                Map<Integer, IndexAndMetadata> partitionMetadataMap = indexAndMetadataMap.get(topic);
                if (partitionMetadataMap == null) {
                    partitionMetadataMap = Maps.newHashMap();
                    indexAndMetadataMap.put(topic, partitionMetadataMap);
                }
                OffsetAndMetadata offsetAndMetadata = offsetAndMetadataTable.get(topic, partition);
                partitionMetadataMap.put(partition, new IndexAndMetadata(offsetAndMetadata.getOffset(), offsetAndMetadata.getMetadata()));
            }
        }
        return indexAndMetadataMap;
    }

    protected Table<Broker, String, Set<Integer>> splitPartitionByBroker(Table<String, Integer, OffsetAndMetadata> offsetAndMetadataTable) {
        HashMultimap<String, Integer> topicAndPartitions = HashMultimap.create();
        for (String topic : offsetAndMetadataTable.rowKeySet()) {
            for (Map.Entry<Integer, OffsetAndMetadata> entry : offsetAndMetadataTable.row(topic).entrySet()) {
                topicAndPartitions.put(topic, entry.getKey());
            }
        }
        return splitPartitionByBroker(topicAndPartitions);
    }

    protected Table<Broker, String, Set<Integer>> splitPartitionByBroker(HashMultimap<String, Integer> topicAndPartitions) {
        Table<Broker, String, Set<Integer>> result = HashBasedTable.create();
        for (String topic : topicAndPartitions.keySet()) {
            TopicConfig topicConfig = clusterManager.getNameService().getTopicConfig(TopicName.parse(topic));
            if (topicConfig == null) {
                continue;
            }
            Set<Integer> partitions = topicAndPartitions.get(topic);
            for (int partition : partitions) {
                Broker broker = topicConfig.fetchBrokerByPartition((short) partition);
                if (broker == null) {
                    logger.error("get offset sync leader failed, topic {}, partition {}, leader not available", topic, partition);
                    continue;
                }
                Set<Integer> partitionSet = result.get(broker, topic);
                if (partitionSet == null) {
                    partitionSet = Sets.newHashSet();
                    result.put(broker, topic, partitionSet);
                }
                partitionSet.add(partition);
            }
        }
        return result;
    }
}