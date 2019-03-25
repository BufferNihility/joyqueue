package com.jd.journalq.broker.coordinator.domain;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jd.journalq.toolkit.time.SystemClock;

import java.util.List;
import java.util.Map;

/**
 * CoordinatorGroupMember
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/4
 */
public class CoordinatorGroupMember {

    private String id;
    private String groupId;
    private String connectionId;
    private String connectionHost;
    private long latestHeartbeat;
    private int sessionTimeout;
    private Map<String, List<Short>> assignments;
    private List<Short> assignmentList;

    public CoordinatorGroupMember() {
    }

    public CoordinatorGroupMember(String id, String groupId, String connectionId, String connectionHost, int sessionTimeout) {
        this.id = id;
        this.groupId = groupId;
        this.connectionId = connectionId;
        this.connectionHost = connectionHost;
        this.sessionTimeout = sessionTimeout;
    }

    public boolean isExpired() {
        return (latestHeartbeat + sessionTimeout) < SystemClock.now();
    }

    public void setAssignedTopicPartitions(String topic, List<Short> partitions) {
        Map<String, List<Short>> assignments = getOrCreateAssignments();
        assignments.put(topic, partitions);
    }

    public void addAssignedPartition(String topic, short partition) {
        getAssignedTopicPartitions(topic).add(partition);
    }

    public void removeAssignedPartition(String topic, short partition) {
        if (assignments == null) {
            return;
        }
        List<Short> partitions = assignments.get(topic);
        if (partitions == null) {
            return;
        }
        partitions.remove((Object) partition);
    }

    public List<Short> getAssignedTopicPartitions(String topic) {
        Map<String, List<Short>> assignments = getOrCreateAssignments();
        List<Short> partitionList = assignments.get(topic);
        if (partitionList == null) {
            partitionList = Lists.newArrayList();
            assignments.put(topic, partitionList);
        }
        return partitionList;
    }

    protected Map<String, List<Short>> getOrCreateAssignments() {
        if (assignments == null) {
            assignments = Maps.newHashMap();
        }
        return assignments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getConnectionHost() {
        return connectionHost;
    }

    public void setConnectionHost(String connectionHost) {
        this.connectionHost = connectionHost;
    }

    public long getLatestHeartbeat() {
        return latestHeartbeat;
    }

    public void setLatestHeartbeat(long latestHeartbeat) {
        this.latestHeartbeat = latestHeartbeat;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setAssignments(Map<String, List<Short>> assignments) {
        this.assignments = assignments;
    }

    public Map<String, List<Short>> getAssignments() {
        return assignments;
    }

    public void setAssignmentList(List<Short> assignmentList) {
        this.assignmentList = assignmentList;
    }

    public List<Short> getAssignmentList() {
        return assignmentList;
    }
}