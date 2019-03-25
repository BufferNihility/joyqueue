package com.jd.journalq.broker.kafka.network.codec;

import com.google.common.collect.Maps;
import com.jd.journalq.broker.kafka.command.SyncGroupAssignment;
import com.jd.journalq.broker.kafka.message.serializer.KafkaSyncGroupAssignmentSerializer;
import com.jd.journalq.broker.kafka.network.KafkaPayloadCodec;
import com.jd.journalq.broker.kafka.KafkaCommandType;
import com.jd.journalq.broker.kafka.command.SyncGroupRequest;
import com.jd.journalq.broker.kafka.command.SyncGroupResponse;
import com.jd.journalq.broker.kafka.network.KafkaHeader;
import com.jd.journalq.common.network.serializer.Serializer;
import com.jd.journalq.common.network.transport.command.Type;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.Map;

/**
 * SyncGroupCodec
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/5
 */
public class SyncGroupCodec implements KafkaPayloadCodec<SyncGroupResponse>, Type {

    @Override
    public Object decode(KafkaHeader header, ByteBuf buffer) throws Exception {
        SyncGroupRequest request = new SyncGroupRequest();
        Map<String, SyncGroupAssignment> groupAssignment = Collections.emptyMap();

        request.setGroupId(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        request.setGenerationId(buffer.readInt());
        request.setMemberId(Serializer.readString(buffer, Serializer.SHORT_SIZE));

        int size = buffer.readInt();

        if (size > 0) {
            groupAssignment = Maps.newHashMap();
            for (int i = 0; i < size; i++) {
                String memberId = Serializer.readString(buffer, Serializer.SHORT_SIZE);
                SyncGroupAssignment assignment = KafkaSyncGroupAssignmentSerializer.readAssignment(buffer);
                groupAssignment.put(memberId, assignment);
            }
        }

        request.setGroupAssignment(groupAssignment);
        return request;
    }

    @Override
    public void encode(SyncGroupResponse payload, ByteBuf buffer) throws Exception {
        if (payload.getVersion() >= 1) {
            // throttle_time_ms
            buffer.writeInt(payload.getThrottleTimeMs());
        }

        // 错误码
        buffer.writeShort(payload.getErrorCode());

        SyncGroupAssignment assignment = payload.getAssignment();
        if (assignment != null) {
            KafkaSyncGroupAssignmentSerializer.writeAssignment(buffer, assignment);
        } else {
            buffer.writeInt(0);
        }
    }

    @Override
    public int type() {
        return KafkaCommandType.SYNC_GROUP.getCode();
    }
}