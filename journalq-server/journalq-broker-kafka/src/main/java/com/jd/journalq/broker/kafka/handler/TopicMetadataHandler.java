/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.journalq.broker.kafka.handler;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.broker.kafka.KafkaCommandType;
import com.jd.journalq.broker.kafka.KafkaErrorCode;
import com.jd.journalq.broker.kafka.command.TopicMetadataRequest;
import com.jd.journalq.broker.kafka.command.TopicMetadataResponse;
import com.jd.journalq.broker.kafka.model.KafkaBroker;
import com.jd.journalq.broker.kafka.model.KafkaPartitionMetadata;
import com.jd.journalq.broker.kafka.model.KafkaTopicMetadata;
import com.jd.journalq.domain.Broker;
import com.jd.journalq.domain.Partition;
import com.jd.journalq.domain.TopicConfig;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.network.transport.Transport;
import com.jd.journalq.network.transport.command.Command;
import com.jd.journalq.nsr.NameService;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TopicMetadataHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/5
 */
public class TopicMetadataHandler extends AbstractKafkaCommandHandler implements BrokerContextAware {

    protected static final Logger logger = LoggerFactory.getLogger(TopicMetadataHandler.class);

    private NameService nameService;

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.nameService = brokerContext.getNameService();
    }

    @Override
    public Command handle(Transport transport, Command command) {
        TopicMetadataRequest topicMetadataRequest = (TopicMetadataRequest) command.getPayload();
        Map<String, TopicConfig> topicConfigs = getTopicConfigs(topicMetadataRequest.getTopics());
        List<KafkaBroker> brokers = getTopicBrokers(topicConfigs);
        List<KafkaTopicMetadata> topicMetadata = getTopicMetadata(topicMetadataRequest.getTopics(), topicConfigs);

        // TODO 临时日志
        if (CollectionUtils.isEmpty(topicMetadata) || logger.isDebugEnabled()) {
            logger.info("get topic metadata, topics: {}, address: {}, metadata: {}",
                    topicMetadataRequest.getTopics(), transport.remoteAddress(), JSON.toJSONString(topicMetadata));
        }

        TopicMetadataResponse topicMetadataResponse = new TopicMetadataResponse(brokers, topicMetadata);
        return new Command(topicMetadataResponse);
    }

    @Override
    public int type() {
        return KafkaCommandType.METADATA.getCode();
    }

    protected Map<String, TopicConfig> getTopicConfigs(List<TopicName> topics) {
        Map<String, TopicConfig> result = Maps.newHashMap();
        for (TopicName topic : topics) {
            TopicConfig topicConfig = nameService.getTopicConfig(topic);
            if (topicConfig != null) {
                result.put(topic.getFullName(), topicConfig);
            }
        }
        return result;
    }

    protected List<KafkaBroker> getTopicBrokers(Map<String, TopicConfig> topicConfigs) {
        List<KafkaBroker> result = Lists.newLinkedList();
        for (Map.Entry<String, TopicConfig> topicEntry : topicConfigs.entrySet()) {
            for (Map.Entry<Integer, Broker> entry : topicEntry.getValue().fetchAllBroker().entrySet()) {
                Broker broker = entry.getValue();
                KafkaBroker kafkaBroker = new KafkaBroker(broker.getId(), broker.getIp(), broker.getPort());
                result.add(kafkaBroker);
            }
        }
        return result;
    }

    protected List<KafkaTopicMetadata> getTopicMetadata(List<TopicName> topics, Map<String, TopicConfig> topicConfigs) {
        List<KafkaTopicMetadata> result = Lists.newLinkedList();
        for (TopicName topicName : topics) {
            TopicConfig topicConfig = topicConfigs.get(topicName.getFullName());

            if (topicConfig != null) {
                List<KafkaPartitionMetadata> kafkaPartitionMetadata = getPartitionMetadata(topicConfig);
                KafkaTopicMetadata kafkaTopicMetadata = new KafkaTopicMetadata(topicConfig.getName().getFullName(), kafkaPartitionMetadata, KafkaErrorCode.NONE);
                result.add(kafkaTopicMetadata);
            } else {
                KafkaTopicMetadata kafkaTopicMetadata = new KafkaTopicMetadata(topicName.getFullName(), Collections.emptyList(), KafkaErrorCode.TOPIC_AUTHORIZATION_FAILED);
                result.add(kafkaTopicMetadata);
            }
        }
        return result;
    }

    protected List<KafkaPartitionMetadata> getPartitionMetadata(TopicConfig topicConfig) {
        List<KafkaPartitionMetadata> result = Lists.newLinkedList();
        for (Partition partition : topicConfig.fetchPartitionMetadata()) {
            short errorCode = KafkaErrorCode.NONE;
            KafkaBroker leader = null;
            List<KafkaBroker> replicas = Lists.newLinkedList();
            List<KafkaBroker> isrs = Lists.newLinkedList();

            if (partition.getLeader() != null) {
                leader = new KafkaBroker(partition.getLeader().getId(), partition.getLeader().getIp(), partition.getLeader().getPort());
            } else {
                errorCode = KafkaErrorCode.LEADER_NOT_AVAILABLE;
            }

            for (Broker replica : partition.getReplicas()) {
                replicas.add(new KafkaBroker(replica.getId(), replica.getIp(), replica.getPort()));
            }

            for (Broker isr : partition.getIsrs()) {
                isrs.add(new KafkaBroker(isr.getId(), isr.getIp(), isr.getPort()));
            }

            KafkaPartitionMetadata kafkaPartitionMetadata = new KafkaPartitionMetadata(partition.getPartitionId(), leader, replicas, isrs, errorCode);
            result.add(kafkaPartitionMetadata);
        }
        return result;
    }
}
