package com.jd.journalq.broker.mqtt.cluster;

import com.jd.journalq.broker.mqtt.subscriptions.TopicFilter;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.common.domain.ClientType;
import com.jd.journalq.common.domain.Subscription;
import com.jd.journalq.common.domain.TopicName;
import com.jd.journalq.nsr.NameService;
import com.jd.journalq.broker.mqtt.subscriptions.MqttSubscription;
import com.jd.journalq.toolkit.service.Service;
import io.netty.util.internal.ConcurrentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author majun8
 */
public class MqttSubscriptionManager extends Service {
    private static Logger LOG = LoggerFactory.getLogger(MqttSubscriptionManager.class);

    private Set<String> topics = new ConcurrentSet<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private NameService nameService;

    public MqttSubscriptionManager(BrokerContext brokerContext) {
        this.nameService = brokerContext.getNameService();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        scheduler.scheduleWithFixedDelay(new ScheduledTopicsUpdater(), 0,60, TimeUnit.SECONDS);
        LOG.info("subscribe service is started.");
    }

    @Override
    protected void doStop() {
        super.doStop();
    }

    public Set<MqttSubscription> subscribes(String clientGroupName, List<MqttSubscription> topicFilters) throws Exception {
        Set<MqttSubscription> needSubscriptions = new HashSet<>();
        if (topicFilters == null || topicFilters.isEmpty()) {
            LOG.info("Subscribe topic list empty, please check topicFilters: {}", topicFilters);
            return needSubscriptions;
        }

        filterTopics(needSubscriptions, topicFilters);

        if (needSubscriptions.isEmpty()) {
            LOG.info("Subscribe topic list empty, please check topicFilters: {}", topicFilters);
            return needSubscriptions;
        }

        List<Subscription> subscriptionList = new ArrayList<>(needSubscriptions.size());
        for (MqttSubscription subscription : needSubscriptions) {
            subscriptionList.add(new Subscription(TopicName.parse(subscription.getTopicFilter().toString()), clientGroupName, Subscription.Type.CONSUMPTION));
        }
        nameService.subscribe(subscriptionList, ClientType.MQTT);

        return needSubscriptions;
    }

    public void unSubscribe(String clientGroupName, Set<MqttSubscription> topicFilters) throws Exception {
        if (topicFilters == null || topicFilters.isEmpty()) {
            LOG.info("UnSubscribe topic list empty, please check topicFilters: {}", topicFilters);
        }

        List<Subscription> unSubscriptionList = new ArrayList<>(topicFilters.size());
        for (MqttSubscription subscription : topicFilters) {
            unSubscriptionList.add(new Subscription(TopicName.parse(subscription.getTopicFilter().toString()), clientGroupName, Subscription.Type.CONSUMPTION));
        }
        nameService.unSubscribe(unSubscriptionList);
    }

    private void filterTopics(Set<MqttSubscription> list, List<MqttSubscription> topicFilters) {
        if (topics.size() == 0) {
            list.addAll(topicFilters);
        } else {
            for (MqttSubscription subscription : topicFilters) {
                for (String topic : topics) {
                    TopicFilter newTopicFilter = new TopicFilter(topic);
                    if (newTopicFilter.match(subscription.getTopicFilter())) {
                        MqttSubscription newSubscription = new MqttSubscription(subscription.getClientId(), newTopicFilter, subscription.getRequestedQos());
                        list.add(newSubscription);
                    }
                }
            }
        }
    }

    private class ScheduledTopicsUpdater implements Runnable {

        @Override
        public void run() {
            // todo topic主题不区分类型 所以获取全部主题量会很大 这里后续要优化
            Set<String> origTopics = nameService.getAllTopics();
            if (origTopics != null && origTopics.size() > 0) {
                LOG.info("Topic updater data size: {}", origTopics.size());
                for (String tn : origTopics) {
                    topics.add(tn);
                }
            } else {
                LOG.info("Topic updater data empty.");
            }
        }
    }
}