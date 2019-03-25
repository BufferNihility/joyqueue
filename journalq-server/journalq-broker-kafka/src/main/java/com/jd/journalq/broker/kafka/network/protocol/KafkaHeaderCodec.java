package com.jd.journalq.broker.kafka.network.protocol;

import com.jd.journalq.broker.kafka.network.KafkaHeader;
import com.jd.journalq.common.domain.QosLevel;
import com.jd.journalq.common.network.transport.codec.Codec;
import com.jd.journalq.common.network.transport.command.Direction;
import com.jd.journalq.common.network.serializer.Serializer;
import com.jd.journalq.common.network.transport.exception.TransportException;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by zhuduohui on 2018/9/2.
 */
public class KafkaHeaderCodec implements Codec {
    private static Logger logger = LoggerFactory.getLogger(KafkaHeaderCodec.class);

    @Override
    public KafkaHeader decode(ByteBuf buffer) throws TransportException.CodecException {
        KafkaHeader kafkaHeader = new KafkaHeader();

        kafkaHeader.setApiKey(buffer.readShort());
        kafkaHeader.setApiVersion(buffer.readShort());
        kafkaHeader.setRequestId(buffer.readInt());
        try {
            kafkaHeader.setClientId(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        } catch (Exception e) {
            throw new TransportException.CodecException(e);
        }

        if (kafkaHeader.getDirection() == null) kafkaHeader.setDirection(Direction.REQUEST);
        if (kafkaHeader.getQosLevel() == null) kafkaHeader.setQosLevel(QosLevel.RECEIVE);

        return kafkaHeader;
    }

    @Override
    public void encode(Object obj, ByteBuf buffer) throws TransportException.CodecException {
        KafkaHeader kafkaHeader = (KafkaHeader)obj;
        buffer.writeInt(kafkaHeader.getRequestId());
    }
}