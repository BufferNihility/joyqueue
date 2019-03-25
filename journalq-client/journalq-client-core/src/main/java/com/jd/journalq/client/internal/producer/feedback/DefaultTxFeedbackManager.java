package com.jd.journalq.client.internal.producer.feedback;

import com.google.common.collect.Maps;
import com.jd.journalq.client.internal.cluster.ClusterManager;
import com.jd.journalq.client.internal.metadata.domain.TopicMetadata;
import com.jd.journalq.client.internal.nameserver.NameServerConfig;
import com.jd.journalq.client.internal.nameserver.helper.NameServerHelper;
import com.jd.journalq.client.internal.producer.MessageSender;
import com.jd.journalq.client.internal.producer.TxFeedbackManager;
import com.jd.journalq.client.internal.producer.callback.TxFeedbackCallback;
import com.jd.journalq.client.internal.producer.exception.ProducerException;
import com.jd.journalq.client.internal.producer.feedback.config.TxFeedbackConfig;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.service.Service;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * TxFeedbackManager
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/24
 */
public class DefaultTxFeedbackManager extends Service implements TxFeedbackManager {

    protected static final Logger logger = LoggerFactory.getLogger(DefaultTxFeedbackManager.class);

    private TxFeedbackConfig config;
    private NameServerConfig nameServerConfig;
    private ClusterManager clusterManager;
    private MessageSender messageSender;

    private ConcurrentMap<String, TxFeedbackScheduler> txCallback = Maps.newConcurrentMap();

    public DefaultTxFeedbackManager(TxFeedbackConfig config, NameServerConfig nameServerConfig, ClusterManager clusterManager, MessageSender messageSender) {
        Preconditions.checkArgument(config != null, "feedback not null");
        Preconditions.checkArgument(nameServerConfig != null, "nameServer not null");
        Preconditions.checkArgument(clusterManager != null, "clusterManager not null");
        Preconditions.checkArgument(messageSender != null, "messageSender not null");
        Preconditions.checkArgument(StringUtils.isNotBlank(config.getApp()), "feedback.app not blank");
        Preconditions.checkArgument(config.getTimeout() > config.getLongPollTimeout(), "feedback.pollTimeout must be greater than consumer.longPullTimeout");

        this.config = config;
        this.nameServerConfig = nameServerConfig;
        this.clusterManager = clusterManager;
        this.messageSender = messageSender;
    }

    @Override
    protected void doStart() throws Exception {
        for (Map.Entry<String, TxFeedbackScheduler> entry : txCallback.entrySet()) {
            if (!entry.getValue().isStarted()) {
                entry.getValue().start();
            }
        }
//        logger.info("txFeedbackManager is started");
    }

    @Override
    protected void doStop() {
        for (Map.Entry<String, TxFeedbackScheduler> entry : txCallback.entrySet()) {
            entry.getValue().stop();
        }
        logger.info("txFeedbackManager is stopped");
    }

    @Override
    public synchronized void setTransactionCallback(String topic, TxFeedbackCallback callback) {
        Preconditions.checkArgument(StringUtils.isNotBlank(topic), "topic not blank");
        Preconditions.checkArgument(callback != null, "callback not null");
        checkTopicMetadata(topic);

        String topicFullName = getTopicFullName(topic);

        if (txCallback.containsKey(topicFullName)) {
            throw new IllegalArgumentException(String.format("%s feedback is exist", topic));
        }

        TxFeedbackScheduler txFeedbackScheduler = new TxFeedbackScheduler(config, topicFullName, callback, messageSender, clusterManager);

        if (isStarted()) {
            try {
                txFeedbackScheduler.start();
            } catch (Exception e) {
                logger.error("start feedback callback exception, topic: {}, app: {}", topic, config.getApp(), e);
                throw new ProducerException("start feedback callback exception", JMQCode.CN_UNKNOWN_ERROR.getCode());
            }
        }

        txCallback.put(topic, txFeedbackScheduler);
    }

    @Override
    public synchronized void removeTransactionCallback(String topic) {
        Preconditions.checkArgument(StringUtils.isNotBlank(topic), "topic not blank");

        String topicFullName = getTopicFullName(topic);

        TxFeedbackScheduler txFeedbackScheduler = txCallback.get(topicFullName);
        if (txFeedbackScheduler == null) {
            throw new IllegalArgumentException(String.format("%s feedback is not exist", topic));
        }

        if (txFeedbackScheduler.isStarted()) {
            txFeedbackScheduler.stop();
        }
        txCallback.remove(topic);
    }

    protected TopicMetadata checkTopicMetadata(String topic) {
        TopicMetadata topicMetadata = clusterManager.fetchTopicMetadata(getTopicFullName(topic), config.getApp());
        if (topicMetadata == null) {
            throw new ProducerException(String.format("topic %s is not exist", topic), JMQCode.FW_TOPIC_NOT_EXIST.getCode());
        }
        if (topicMetadata.getProducerPolicy() == null) {
            throw new ProducerException(String.format("topic %s producer %s is not exist", topic, config.getApp()), JMQCode.FW_PRODUCER_NOT_EXISTS.getCode());
        }
        return topicMetadata;
    }

    protected String getTopicFullName(String topic) {
        return NameServerHelper.getTopicFullName(topic, nameServerConfig);
    }

    protected void checkState() {
        if (!isStarted()) {
            throw new UnsupportedOperationException("txFeedbackManager is not started");
        }
    }
}