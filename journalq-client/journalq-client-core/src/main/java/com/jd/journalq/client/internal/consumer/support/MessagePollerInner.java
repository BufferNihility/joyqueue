package com.jd.journalq.client.internal.consumer.support;

import com.google.common.collect.Lists;
import com.jd.journalq.client.internal.cluster.ClusterManager;
import com.jd.journalq.client.internal.consumer.BrokerLoadBalance;
import com.jd.journalq.client.internal.consumer.MessageFetcher;
import com.jd.journalq.client.internal.consumer.callback.ConsumerListener;
import com.jd.journalq.client.internal.consumer.callback.FetchListener;
import com.jd.journalq.client.internal.consumer.callback.PartitionFetchListener;
import com.jd.journalq.client.internal.consumer.config.ConsumerConfig;
import com.jd.journalq.client.internal.consumer.converter.BrokerAssignmentConverter;
import com.jd.journalq.client.internal.consumer.coordinator.domain.BrokerAssignment;
import com.jd.journalq.client.internal.consumer.coordinator.domain.BrokerAssignments;
import com.jd.journalq.client.internal.consumer.domain.ConsumeMessage;
import com.jd.journalq.client.internal.consumer.domain.FetchMessageData;
import com.jd.journalq.client.internal.consumer.exception.ConsumerException;
import com.jd.journalq.client.internal.consumer.transport.ConsumerClient;
import com.jd.journalq.client.internal.consumer.transport.ConsumerClientManager;
import com.jd.journalq.client.internal.metadata.domain.TopicMetadata;
import com.jd.journalq.client.internal.nameserver.NameServerConfig;
import com.jd.journalq.client.internal.nameserver.helper.NameServerHelper;
import com.jd.journalq.client.internal.trace.TraceBuilder;
import com.jd.journalq.client.internal.trace.TraceCaller;
import com.jd.journalq.client.internal.trace.TraceType;
import com.jd.journalq.client.internal.transport.ClientState;
import com.jd.journalq.common.domain.Consumer;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.common.network.domain.BrokerNode;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.service.Service;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MessagePollerInner
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/1/4
 */
public class MessagePollerInner extends Service {

    public static final long FETCH_PARTITION_NONE_INDEX = -1;

    protected static final Logger logger = LoggerFactory.getLogger(MessagePollerInner.class);

    private ConsumerConfig config;
    private NameServerConfig nameServerConfig;
    private ClusterManager clusterManager;
    private ConsumerClientManager consumerClientManager;
    private MessageFetcher messageFetcher;
    private BrokerLoadBalanceManager brokerLoadBalanceManager;

    private String appFullName;

    public MessagePollerInner(ConsumerConfig config, NameServerConfig nameServerConfig, ClusterManager clusterManager, ConsumerClientManager consumerClientManager, MessageFetcher messageFetcher) {
        this.config = config;
        this.nameServerConfig = nameServerConfig;
        this.clusterManager = clusterManager;
        this.consumerClientManager = consumerClientManager;
        this.messageFetcher = messageFetcher;
    }

    @Override
    protected void validate() throws Exception {
        brokerLoadBalanceManager = new BrokerLoadBalanceManager();
    }

    public List<ConsumeMessage> fetchTopic(BrokerNode brokerNode, String topic, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        Preconditions.checkArgument(StringUtils.isNotBlank(topic), "topic not blank");

        TopicMetadata topicMetadata = checkTopicMetadata(topic);
        return fetchTopic(brokerNode, topicMetadata, batchSize, timeout, timeoutUnit, listener);
    }

    public List<ConsumeMessage> fetchTopic(BrokerNode brokerNode, TopicMetadata topicMetadata, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        Preconditions.checkArgument(timeoutUnit != null, "timeoutUnit not null");

        TraceCaller caller = buildTraceCaller(topicMetadata);
        try {
            List<ConsumeMessage> consumeMessages = doFetchTopic(brokerNode, topicMetadata, batchSize, timeout, timeoutUnit, listener);
            caller.end();
            return consumeMessages;
        } catch (Exception e) {
            caller.error();
            if (e instanceof ConsumerException) {
                throw (ConsumerException) e;
            } else {
                throw new ConsumerException(e);
            }
        }
    }

    protected List<ConsumeMessage> doFetchTopic(BrokerNode brokerNode, TopicMetadata topicMetadata, int batchSize, long timeout, TimeUnit timeoutUnit, final ConsumerListener listener) {
        Consumer.ConsumerPolicy consumerPolicy = topicMetadata.getConsumerPolicy();
        timeout = timeoutUnit.toMillis(timeout);
        final String topic = topicMetadata.getTopic();
        final String app = getAppFullName();
        long ackTimeout = (config.getAckTimeout() == ConsumerConfig.NONE_ACK_TIMEOUT ? consumerPolicy.getAckTimeout() : config.getAckTimeout());

        if (listener == null) {
            FetchMessageData fetchMessageData = messageFetcher.fetch(brokerNode, topic, app, batchSize, timeout, ackTimeout, config.getLongPollTimeout());
            return handleFetchMessageData(topic, app, fetchMessageData);
        } else {
            messageFetcher.asyncFetch(brokerNode, topic, app, batchSize, timeout, ackTimeout, config.getLongPollTimeout(), new FetchListener() {
                @Override
                public void onMessage(FetchMessageData fetchMessageData) {
                    try {
                        List<ConsumeMessage> messages = handleFetchMessageData(topic, app, fetchMessageData);
                        listener.onMessage(messages);
                    } catch (Exception e) {
                        listener.onException(e);
                    }
                }

                @Override
                public void onException(Throwable cause) {
                    listener.onException(cause);
                }
            });
            return null;
        }
    }

    public List<ConsumeMessage> fetchPartition(BrokerNode brokerNode, String topic, short partition, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        return fetchPartition(brokerNode, topic, partition, FETCH_PARTITION_NONE_INDEX, batchSize, timeout, timeoutUnit, listener);
    }

    public List<ConsumeMessage> fetchPartition(BrokerNode brokerNode, TopicMetadata topicMetadata, short partition, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        return fetchPartition(brokerNode, topicMetadata, partition, FETCH_PARTITION_NONE_INDEX, batchSize, timeout, timeoutUnit, listener);
    }

    public List<ConsumeMessage> fetchPartition(BrokerNode brokerNode, String topic, short partition, long index, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        Preconditions.checkArgument(StringUtils.isNotBlank(topic), "topic not blank");

        TopicMetadata topicMetadata = checkTopicMetadata(topic);
        return fetchPartition(brokerNode, topicMetadata, partition, index, batchSize, timeout, timeoutUnit, listener);
    }

    public List<ConsumeMessage> fetchPartition(BrokerNode brokerNode, TopicMetadata topicMetadata, short partition, long index, int batchSize, long timeout, TimeUnit timeoutUnit, ConsumerListener listener) {
        Preconditions.checkArgument(topicMetadata != null, "topicMetadata not null");
        Preconditions.checkArgument(timeoutUnit != null, "timeoutUnit not null");

        TraceCaller caller = buildTraceCaller(topicMetadata);
        try {
            List<ConsumeMessage> consumeMessages = doFetchPartition(brokerNode, topicMetadata, partition, index, batchSize, timeout, timeoutUnit, listener);
            caller.end();
            return consumeMessages;
        } catch (Exception e) {
            caller.error();
            if (e instanceof ConsumerException) {
                throw (ConsumerException) e;
            } else {
                throw new ConsumerException(e);
            }
        }
    }

    protected List<ConsumeMessage> doFetchPartition(BrokerNode brokerNode, TopicMetadata topicMetadata, final short partition, long index, int batchSize, long timeout, TimeUnit timeoutUnit, final ConsumerListener listener) {
        timeout = timeoutUnit.toMillis(timeout);
        final String topic = topicMetadata.getTopic();
        final String app = getAppFullName();

        if (listener == null) {
            FetchMessageData fetchMessageData = (index == FETCH_PARTITION_NONE_INDEX ?
                    messageFetcher.fetchPartition(brokerNode, topic, app, partition, batchSize, timeout):
                    messageFetcher.fetchPartition(brokerNode, topic, app, partition, index, batchSize, timeout));
            return handleFetchMessageData(topic, app, fetchMessageData);
        } else {
            PartitionFetchListener partitionFetchListenerAdapter = new PartitionFetchListener() {
                @Override
                public void onMessage(FetchMessageData fetchMessageData) {
                    try {
                        List<ConsumeMessage> consumeMessages = handleFetchMessageData(topic, app, fetchMessageData);
                        listener.onMessage(consumeMessages);
                    } catch (Exception e) {
                        listener.onException(e);
                    }
                }

                @Override
                public void onException(Throwable cause) {
                    listener.onException(cause);
                }
            };

            if (index == FETCH_PARTITION_NONE_INDEX) {
                messageFetcher.fetchPartitionAsync(brokerNode, topic, app, partition, index, batchSize, timeout, partitionFetchListenerAdapter);
            } else {
                messageFetcher.fetchPartitionAsync(brokerNode, topic, app, partition, batchSize, timeout, partitionFetchListenerAdapter);
            }
            return null;
        }
    }

    protected List<ConsumeMessage> handleFetchMessageData(String topic, String app, FetchMessageData fetchMessageData) {
        if (fetchMessageData == null) {
            throw new ConsumerException(JMQCode.CN_UNKNOWN_ERROR.getMessage(), JMQCode.CN_UNKNOWN_ERROR.getCode());
        }

        JMQCode code = fetchMessageData.getCode();
        if (code.equals(JMQCode.SUCCESS)) {
            return fetchMessageData.getMessages();
        }

        // TODO 临时日志
        logger.warn("fetch message error, topic: {}, code: {}, error: {}", topic, code, code.getMessage());

        switch (code) {
            case CN_NO_PERMISSION:
            case CN_SERVICE_NOT_AVAILABLE:
            case FW_FETCH_TOPIC_MESSAGE_BROKER_NOT_LEADER: {
                // 尝试更新元数据
                logger.warn("fetch message error, no permission, topic: {}", topic);
                clusterManager.tryUpdateTopicMetadata(topic, app);
                break;
            }
            case FW_GET_MESSAGE_TOPIC_NOT_READ:
            case FW_FETCH_TOPIC_MESSAGE_PAUSED: {
                logger.debug("fetch message error, not read or paused, topic: {}", topic);
                break;
            }
            case FW_FETCH_MESSAGE_INDEX_OUT_OF_RANGE:
            case SE_INDEX_OVERFLOW:
            case SE_INDEX_UNDERFLOW: {
                logger.warn("fetch message index out of range, reset index, topic: {}, app: {}", topic, app);
                throw new ConsumerException(code.getMessage(), code.getCode());
            }
            case FW_TOPIC_NOT_EXIST: {
                logger.debug("fetch message error, topic not exist, topic: {}", topic);
                throw new ConsumerException(code.getMessage(), code.getCode());
            }
            default: {
                logger.error("fetch message error, topic: {}, code: {}, error: {}", topic, code, code.getMessage());
                break;
            }
        }

        return Collections.emptyList();
    }

    protected TraceCaller buildTraceCaller(TopicMetadata topicMetadata) {
        return TraceBuilder.newInstance()
                .topic(topicMetadata.getTopic())
                .app(getAppFullName())
                .namespace(nameServerConfig.getNamespace())
                .type(TraceType.CONSUMER_FETCH)
                .begin();
    }

    public BrokerAssignments filterRegionBrokers(TopicMetadata topicMetadata, BrokerAssignments brokerAssignments) {
        if (!topicMetadata.getConsumerPolicy().getNearby() || CollectionUtils.isEmpty(brokerAssignments.getAssignments())) {
            return brokerAssignments;
        }
        List<BrokerAssignment> newAssignments = null;
        for (BrokerAssignment brokerAssignment : brokerAssignments.getAssignments()) {
            if (brokerAssignment.getBroker().isNearby()) {
                continue;
            }
            if (newAssignments == null) {
                newAssignments = Lists.newArrayList(brokerAssignments.getAssignments());
            }
            newAssignments.remove(brokerAssignment);
        }
        if (newAssignments == null) {
            return brokerAssignments;
        }
        return new BrokerAssignments(newAssignments);
    }

    public BrokerAssignments filterNotAvailableBrokers(BrokerAssignments brokerAssignments) {
        if (CollectionUtils.isEmpty(brokerAssignments.getAssignments())) {
            return brokerAssignments;
        }
        List<BrokerAssignment> newAssignments = null;
        for (BrokerAssignment brokerAssignment : brokerAssignments.getAssignments()) {
            ConsumerClient client = consumerClientManager.tryGetClient(brokerAssignment.getBroker());
            if (client == null || client.getState().equals(ClientState.CONNECTED)) {
                continue;
            }
            if (newAssignments == null) {
                newAssignments = Lists.newArrayList(brokerAssignments.getAssignments());
            }
            newAssignments.remove(brokerAssignment);
        }
        if (newAssignments == null) {
            return brokerAssignments;
        }
        return new BrokerAssignments(newAssignments);
    }

    public BrokerLoadBalance getBrokerLoadBalance(String topic) {
        return brokerLoadBalanceManager.getBrokerLoadBalance(topic, config.getLoadBalanceType());
    }

    public List<ConsumeMessage> buildPollEmptyResult(ConsumerListener listener) {
        if (listener == null) {
            return Collections.emptyList();
        } else {
            listener.onMessage((List) Collections.emptyList());
            return null;
        }
    }

    public BrokerAssignments buildAllBrokerAssignments(TopicMetadata topicMetadata) {
        return BrokerAssignmentConverter.convertBrokerAssignments(topicMetadata);
    }

    public TopicMetadata checkTopicMetadata(String topic) {
        TopicMetadata topicMetadata = clusterManager.fetchTopicMetadata(getTopicFullName(topic), getAppFullName());
        if (topicMetadata == null) {
            throw new ConsumerException(String.format("topic %s is not exist", topic), JMQCode.FW_TOPIC_NOT_EXIST.getCode());
        }
        if (topicMetadata.getConsumerPolicy() == null) {
            throw new ConsumerException(String.format("topic %s consumer %s is not exist", topic, nameServerConfig.getApp()), JMQCode.FW_CONSUMER_NOT_EXISTS.getCode());
        }
        return topicMetadata;
    }

    public String getTopicFullName(String topic) {
        return NameServerHelper.getTopicFullName(topic, nameServerConfig);
    }

    // TODO group处理
    public String getAppFullName() {
        if (appFullName == null) {
            if (StringUtils.isBlank(config.getGroup())) {
                appFullName = config.getApp();
            } else {
                appFullName = config.getApp() + "." + config.getGroup();
            }
        }
        return appFullName;
    }
}