package com.jd.journalq.broker.kafka.session;

import com.jd.journalq.broker.kafka.command.FetchRequest;
import com.jd.journalq.broker.kafka.command.FindCoordinatorRequest;
import com.jd.journalq.broker.kafka.command.ProduceRequest;
import com.jd.journalq.common.domain.TopicName;
import com.jd.journalq.common.network.transport.command.Command;
import com.jd.journalq.common.network.transport.ChannelTransport;
import com.jd.journalq.common.network.transport.TransportHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * kafka连接处理
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/7/3
 */
@ChannelHandler.Sharable
public class KafkaConnectionHandler extends ChannelDuplexHandler {

    protected static final Logger logger = LoggerFactory.getLogger(KafkaConnectionHandler.class);

    private KafkaConnectionManager kafkaConnectionManager;

    public KafkaConnectionHandler(KafkaConnectionManager kafkaConnectionManager) {
        this.kafkaConnectionManager = kafkaConnectionManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Command) {
            this.connectionStatistic(ctx, (Command) msg);
        }
        super.channelRead(ctx, msg);
    }

    protected void connectionStatistic(ChannelHandlerContext ctx, Command command) {
        KafkaConnectionManager kafkaConnectionManager = this.kafkaConnectionManager;
        Channel channel = ctx.channel();
        Object payload = command.getPayload();
        ChannelTransport transport = TransportHelper.getTransport(channel);

        if (payload instanceof FetchRequest) {
            FetchRequest fetchRequest = (FetchRequest) payload;
            kafkaConnectionManager.addConnection(transport, fetchRequest.getClientId(), String.valueOf(fetchRequest.getVersion()));
            for (TopicName topic : fetchRequest.getRequestInfo().rowKeySet()) {
                kafkaConnectionManager.addConsumer(transport, topic.getFullName());
            }
        } else if (payload instanceof ProduceRequest) {
            ProduceRequest produceRequest = (ProduceRequest) payload;
            kafkaConnectionManager.addConnection(transport, produceRequest.getClientId(), String.valueOf(produceRequest.getVersion()));
            for (TopicName topic : produceRequest.getTopicPartitionMessages().rowKeySet()) {
                kafkaConnectionManager.addProducer(transport, topic.getFullName());
            }
        } else if (payload instanceof FindCoordinatorRequest) {
            FindCoordinatorRequest findCoordinatorRequest = (FindCoordinatorRequest) payload;
            kafkaConnectionManager.addGroup(transport, findCoordinatorRequest.getGroupId());
        }
    }
}