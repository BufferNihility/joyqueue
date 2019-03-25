package com.jd.journalq.broker.replication;

import com.jd.journalq.broker.election.TopicPartitionGroup;


/**
 * author: zhuduohui
 * email: zhuduohui@jd.com
 * date: 2018/9/26
 */
public class Replica {
    private TopicPartitionGroup topicPartitionGroup;

    // replica id
    private int replicaId = 0;

    // replica address
    private String address;

    // write position of this replica
    private long writePosition = 0;

    // commit position of this replica
    // leader replica send commit position to follower replica
    private long commitPosition = 0;

    // next position which leader will send to replica
    private long nextPosition = 0;

    // if the log of this replica match with with leader
    private boolean match = false;

    private long lastReplicateMessageTime;

    private long lastReplicateConsumePosTime;


    Replica(int replicaId, String address) {
        this.replicaId = replicaId;
        this.address = address;
    }

    public TopicPartitionGroup getTopicPartitionGroup() {
        return topicPartitionGroup;
    }

    public void setTopicPartitionGroup(TopicPartitionGroup topicPartitionGroup) {
        this.topicPartitionGroup = topicPartitionGroup;
    }

    int replicaId() {
        return replicaId;
    }

    public void replicaId(int replicaId) {
        this.replicaId = replicaId;
    }

    public String getAddress() {
        return address;
    }

    public String getIp() {
        return address.split(":")[0];
    }

    public void setAddress(String address) {
        this.address = address;
    }

    long writePosition() {
        return writePosition;
    }

    void writePosition(long writePosition) {
        this.writePosition = writePosition;
    }

    public long commitPosition() {
        return commitPosition;
    }

    public void commitPosition(long commitPosition) {
        this.commitPosition = commitPosition;
    }

    long nextPosition() {
        return nextPosition;
    }

    void nextPosition(long nextPosition) {
        this.nextPosition = nextPosition;
    }


    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    void lastReplicateConsumePosTime(long lastReplicateConsumePosTime) {
        this.lastReplicateConsumePosTime = lastReplicateConsumePosTime;
    }

    long lastReplicateConsumePosTime() {
        return lastReplicateConsumePosTime;
    }

    void lastReplicateMessageTime(long lastReplicateMessageTime) {
        this.lastReplicateMessageTime = lastReplicateMessageTime;
    }

    long lastReplicateMessageTime() {
        return lastReplicateMessageTime;
    }

    @Override
    public String toString() {
        return new StringBuilder("Replica:{").append("replicaId:").append(replicaId)
                .append(", address:").append(address)
                .append(", writePosition:").append(writePosition)
                .append(", commitPosition:").append(commitPosition)
                .append(", nextPosition:").append(nextPosition)
                .append(", match:").append(match)
                .append(", lastReplicateMessageTime:").append(lastReplicateMessageTime)
                .append(", lastReplicateConsumePosTime:").append(lastReplicateConsumePosTime).toString();

    }
 }