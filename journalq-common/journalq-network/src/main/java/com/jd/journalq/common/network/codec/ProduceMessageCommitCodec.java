package com.jd.journalq.common.network.codec;

import com.jd.journalq.common.network.command.JMQCommandType;
import com.jd.journalq.common.network.command.ProduceMessageCommit;
import com.jd.journalq.common.network.serializer.Serializer;
import com.jd.journalq.common.network.transport.codec.JMQHeader;
import com.jd.journalq.common.network.transport.codec.PayloadCodec;
import com.jd.journalq.common.network.transport.command.Type;
import io.netty.buffer.ByteBuf;

/**
 * ProduceMessageCommitCodec
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/19
 */
public class ProduceMessageCommitCodec implements PayloadCodec<JMQHeader, ProduceMessageCommit>, Type {

    @Override
    public ProduceMessageCommit decode(JMQHeader header, ByteBuf buffer) throws Exception {
        ProduceMessageCommit produceMessageCommit = new ProduceMessageCommit();
        produceMessageCommit.setTopic(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        produceMessageCommit.setApp(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        produceMessageCommit.setTxId(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        return produceMessageCommit;
    }

    @Override
    public void encode(ProduceMessageCommit payload, ByteBuf buffer) throws Exception {
        Serializer.write(payload.getTopic(), buffer, Serializer.SHORT_SIZE);
        Serializer.write(payload.getApp(), buffer, Serializer.SHORT_SIZE);
        Serializer.write(payload.getTxId(), buffer, Serializer.SHORT_SIZE);
    }

    @Override
    public int type() {
        return JMQCommandType.PRODUCE_MESSAGE_COMMIT.getCode();
    }
}