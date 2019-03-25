package com.jd.journalq.client.internal.consumer.support;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.jd.journalq.client.internal.consumer.MessageFetcher;
import com.jd.journalq.client.internal.consumer.callback.BatchFetchListener;
import com.jd.journalq.client.internal.consumer.callback.BatchPartitionFetchListener;
import com.jd.journalq.client.internal.consumer.callback.FetchListener;
import com.jd.journalq.client.internal.consumer.callback.PartitionFetchListener;
import com.jd.journalq.client.internal.consumer.config.FetcherConfig;
import com.jd.journalq.client.internal.consumer.converter.BrokerMessageConverter;
import com.jd.journalq.client.internal.consumer.domain.FetchMessageData;
import com.jd.journalq.client.internal.consumer.transport.ConsumerClient;
import com.jd.journalq.client.internal.consumer.transport.ConsumerClientGroup;
import com.jd.journalq.client.internal.consumer.transport.ConsumerClientManager;
import com.jd.journalq.client.internal.exception.ClientException;
import com.jd.journalq.client.internal.transport.ConnectionState;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.common.network.command.FetchPartitionMessageAck;
import com.jd.journalq.common.network.command.FetchTopicMessageAck;
import com.jd.journalq.common.network.domain.BrokerNode;
import com.jd.journalq.common.network.transport.command.Command;
import com.jd.journalq.common.network.transport.command.CommandCallback;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DefaultMessageFetcher
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/7
 */
public class DefaultMessageFetcher extends Service implements MessageFetcher {

    protected static final Logger logger = LoggerFactory.getLogger(DefaultMessageFetcher.class);

    private FetcherConfig config;
    private ConsumerClientManager consumerClientManager;
    private ConnectionState connectionState = new ConnectionState();

    public DefaultMessageFetcher(ConsumerClientManager consumerClientManager, FetcherConfig config) {
        Preconditions.checkArgument(consumerClientManager != null, "consumerClientManager not null");

        this.consumerClientManager = consumerClientManager;
        this.config = config;
    }

    @Override
    public FetchMessageData fetch(BrokerNode brokerNode, String topic, String app, int count, long timeout, long ackTimeout, long longPollTimeout) {
        Map<String, FetchMessageData> fetchMessageMap = batchFetch(brokerNode, Lists.newArrayList(topic), app, count, timeout, ackTimeout, longPollTimeout);
        return fetchMessageMap.get(topic);
    }

    @Override
    public void asyncFetch(BrokerNode brokerNode, final String topic, String app, int count, long timeout, long ackTimeout, long longPollTimeout, final FetchListener listener) {
        batchFetchAsync(brokerNode, Lists.newArrayList(topic), app, count, timeout, ackTimeout, longPollTimeout, new BatchFetchListener() {
            @Override
            public void onMessage(Map<String, FetchMessageData> fetchMessageMap) {
                FetchMessageData fetchMessageData = fetchMessageMap.get(topic);
                listener.onMessage(fetchMessageData);
            }

            @Override
            public void onException(Throwable cause) {
                listener.onException(cause);
            }
        });
    }

    @Override
    public FetchMessageData fetchPartition(BrokerNode brokerNode, String topic, String app, short partition, int count, long timeout) {
        Map<String, Short> partitions = Maps.newHashMap();
        partitions.put(topic, partition);
        Table<String, Short, FetchMessageData> fetchMessageTable = batchFetchPartitions(brokerNode, partitions, app, count, timeout);
        return fetchMessageTable.get(topic, partition);
    }

    @Override
    public void fetchPartitionAsync(BrokerNode brokerNode, final String topic, String app, final short partition, int count, long timeout, final PartitionFetchListener listener) {
        Map<String, Short> partitions = Maps.newHashMap();
        partitions.put(topic, partition);
        batchFetchPartitionsAsync(brokerNode, partitions, app, count, timeout, new BatchPartitionFetchListener() {
            @Override
            public void onMessage(Table<String, Short, FetchMessageData> fetchMessageTable) {
                FetchMessageData fetchMessageData = fetchMessageTable.get(topic, partition);
                listener.onMessage(fetchMessageData);
            }

            @Override
            public void onException(Throwable cause) {
                listener.onException(cause);
            }
        });
    }

    @Override
    public FetchMessageData fetchPartition(BrokerNode brokerNode, String topic, String app, short partition, long index, int count, long timeout) {
        Table<String, Short, Long> partitionTable = HashBasedTable.create();
        partitionTable.put(topic, partition, index);
        Table<String, Short, FetchMessageData> fetchMessageTable = batchFetchPartitions(brokerNode, partitionTable, app, count, timeout);
        return fetchMessageTable.get(topic, partition);
    }

    @Override
    public void fetchPartitionAsync(BrokerNode brokerNode, final String topic, final String app, final short partition, final long index, int count, long timeout, final PartitionFetchListener listener) {
        Table<String, Short, Long> partitionTable = HashBasedTable.create();
        partitionTable.put(topic, partition, index);
        batchFetchPartitionsAsync(brokerNode, partitionTable, app, count, timeout, new BatchPartitionFetchListener() {
            @Override
            public void onMessage(Table<String, Short, FetchMessageData> fetchMessageTable) {
                FetchMessageData fetchMessageData = fetchMessageTable.get(topic, partition);
                listener.onMessage(fetchMessageData);
            }

            @Override
            public void onException(Throwable cause) {
                listener.onException(cause);
            }
        });
    }

    @Override
    public Map<String, FetchMessageData> batchFetch(BrokerNode brokerNode, List<String> topics, String app, int count, long timeout, long ackTimeout, long longPollTimeout) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, topics, app, client);

        FetchTopicMessageAck fetchTopicMessageAck = client.fetchTopicMessage(topics, app, count, timeout, ackTimeout, longPollTimeout);
        return BrokerMessageConverter.convert(app, fetchTopicMessageAck.getData());
    }

    @Override
    public void batchFetchAsync(BrokerNode brokerNode, final List<String> topics, final String app, int count, long timeout, long ackTimeout, long longPollTimeout, final BatchFetchListener listener) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, topics, app, client);

        try {
            client.asyncFetchTopicMessage(topics, app, count, timeout, ackTimeout, longPollTimeout, new CommandCallback() {
                @Override
                public void onSuccess(Command request, Command response) {
                    FetchTopicMessageAck fetchTopicMessageAck = (FetchTopicMessageAck) response.getPayload();
                    Map<String, FetchMessageData> consumeMessages = BrokerMessageConverter.convert(app, fetchTopicMessageAck.getData());
                    listener.onMessage(consumeMessages);
                }

                @Override
                public void onException(Command request, Throwable cause) {
                    listener.onException(cause);
                }
            });
        } catch (ClientException e) {
            listener.onException(e);
        }
    }

    @Override
    public Table<String, Short, FetchMessageData> batchFetchPartitions(BrokerNode brokerNode, Map<String, Short> partitions, String app, int count, long timeout) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, partitions.keySet(), app, client);

        FetchPartitionMessageAck fetchPartitionMessageAck = client.fetchPartitionMessage(partitions, app, count, timeout);
        return BrokerMessageConverter.convert(app, fetchPartitionMessageAck.getData());
    }

    @Override
    public void batchFetchPartitionsAsync(BrokerNode brokerNode, Map<String, Short> partitions, final String app, int count, long timeout, final BatchPartitionFetchListener listener) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, partitions.keySet(), app, client);

        client.asyncFetchPartitionMessage(partitions, app, count, timeout, new CommandCallback() {
            @Override
            public void onSuccess(Command request, Command response) {
                FetchPartitionMessageAck fetchPartitionMessageAck = (FetchPartitionMessageAck) response.getPayload();
                Table<String, Short, FetchMessageData> fetchMessageDataTable = BrokerMessageConverter.convert(app, fetchPartitionMessageAck.getData());
                listener.onMessage(fetchMessageDataTable);
            }

            @Override
            public void onException(Command request, Throwable cause) {
                listener.onException(cause);
            }
        });
    }

    @Override
    public Table<String, Short, FetchMessageData> batchFetchPartitions(BrokerNode brokerNode, Table<String, Short, Long> partitions, String app, int count, long timeout) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, partitions.rowKeySet(), app, client);

        FetchPartitionMessageAck fetchPartitionMessageAck = client.fetchPartitionMessage(partitions, app, count, timeout);
        return BrokerMessageConverter.convert(app, fetchPartitionMessageAck.getData());
    }

    @Override
    public void batchFetchPartitionsAsync(BrokerNode brokerNode, Table<String, Short, Long> partitions, final String app, int count, long timeout, final BatchPartitionFetchListener listener) {
        checkState();
        ConsumerClient client = consumerClientManager.getOrCreateClient(brokerNode);
        handleAddConsumers(brokerNode, partitions.rowKeySet(), app, client);

        client.asyncFetchPartitionMessage(partitions, app, count, timeout, new CommandCallback() {
            @Override
            public void onSuccess(Command request, Command response) {
                FetchPartitionMessageAck fetchPartitionMessageAck = (FetchPartitionMessageAck) response.getPayload();
                Table<String, Short, FetchMessageData> fetchMessageDataTable = BrokerMessageConverter.convert(app, fetchPartitionMessageAck.getData());
                listener.onMessage(fetchMessageDataTable);
            }

            @Override
            public void onException(Command request, Throwable cause) {
                listener.onException(cause);
            }
        });
    }

    protected void checkState() {
        if (!isStarted()) {
            throw new ClientException("fetcher is not started", JMQCode.CN_SERVICE_NOT_AVAILABLE.getCode());
        }
    }

    protected void handleAddConsumers(BrokerNode brokerNode, Collection<String> topics, String app, ConsumerClient client) {
        client.addConsumers(topics, app);
        connectionState.addBrokerNode(brokerNode);
        connectionState.addTopics(topics);
        connectionState.addApp(app);
    }

    @Override
    protected void doStop() {
        handleRemoveConsumers();
    }

    protected void handleRemoveConsumers() {
        Set<BrokerNode> brokerNodes = connectionState.getBrokerNodes();
        Set<String> topics = connectionState.getTopics();
        Set<String> apps = connectionState.getApps();

        for (BrokerNode brokerNode : brokerNodes) {
            handleRemoveConsumers(brokerNode, topics, apps);
        }
    }

    protected void handleRemoveConsumers(BrokerNode brokerNode, Set<String> topics, Set<String> apps) {
        ConsumerClientGroup clientGroup = consumerClientManager.getClientGroup(brokerNode);
        if (clientGroup == null) {
            return;
        }
        for (String app : apps) {
            for (ConsumerClient client : clientGroup.getClients()) {
                try {
                    client.removeConsumers(topics, app);
                } catch (Exception e) {
                    logger.warn("remove consumers exception, topics: {}, app: {}, exception: {}", topics, app, e.getMessage());
                    logger.debug("remove consumers exception, topics: {}, app: {}", topics, app, e);
                }
            }
        }
    }
}