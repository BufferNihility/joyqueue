package com.jd.journalq.common.network.transport.codec;

import com.jd.journalq.common.domain.QosLevel;
import com.jd.journalq.common.network.transport.command.Direction;
import com.jd.journalq.common.network.serializer.Serializer;
import com.jd.journalq.common.network.transport.exception.TransportException;
import io.netty.buffer.ByteBuf;

/**
 * jmq协议头编解码器
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/8/21
 */
public class JMQHeaderCodec implements Codec {

    // MAGIC + VERSION + FLAG + REQUESTID + COMMANDTYPE + SENDTIME + STATUS + ERRORMSG
    public static final int HEADER_LENGTH = 4 + 1 + 1 + 4 + 1 + 8;

    @Override
    public JMQHeader decode(ByteBuf buffer) throws TransportException.CodecException {
        if (buffer.readableBytes() < HEADER_LENGTH) {
            return null;
        }

        int magic = buffer.readInt();
        if (magic != JMQHeader.MAGIC) {
            return null;
        }

        byte version = buffer.readByte();
        byte identity = buffer.readByte();
        int requestId = buffer.readInt();
        byte type = buffer.readByte();
        long sendTime = buffer.readLong();
        short status = 0;
        String error = null;
        Direction direction = Direction.valueOf(identity & 0x1);
        QosLevel qosLevel = QosLevel.valueOf((identity >> 1) & 0x3);

        if (direction.equals(Direction.RESPONSE)) {
            // 1个字节的状态码
            status = buffer.readUnsignedByte();
            // 2个字节的异常长度
            // 异常信息
            try {
                error = Serializer.readString(buffer, 2);
            } catch (Exception e) {
                throw new TransportException.CodecException(e.getMessage());
            }
        }

        return new JMQHeader(version, qosLevel, direction, requestId, type, sendTime, status, error);
    }

    @Override
    public void encode(Object payload, ByteBuf buffer) throws TransportException.CodecException {
        JMQHeader header = (JMQHeader) payload;
        // 响应类型
        byte identity = (byte) ((header.getDirection().ordinal() & 0x1) | ((header.getQosLevel().ordinal()) << 1 & 0x6));

        buffer.writeInt(JMQHeader.MAGIC);
        buffer.writeByte(header.getVersion());
        buffer.writeByte(identity);
        buffer.writeInt(header.getRequestId());
        buffer.writeByte(header.getType());
        buffer.writeLong(header.getTime());
        if (header.getDirection().equals(Direction.RESPONSE)) {
            buffer.writeByte(header.getStatus());
            try {
                Serializer.write(header.getError(), buffer, 2);
            } catch (Exception e) {
                throw new TransportException.CodecException(e.getMessage());
            }
        }
    }
}