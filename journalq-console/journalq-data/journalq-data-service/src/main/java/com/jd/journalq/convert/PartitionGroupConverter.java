package com.jd.journalq.convert;

import com.jd.journalq.common.domain.PartitionGroup;
import com.jd.journalq.model.domain.Namespace;
import com.jd.journalq.model.domain.Topic;
import com.jd.journalq.model.domain.TopicPartitionGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  Partition group converter
 *  Created by chenyanying3 on 2018-11-29.
 */
public class PartitionGroupConverter {
    public static TopicPartitionGroup convert(PartitionGroup partitionGroup) {
        TopicPartitionGroup topicPartitionGroup = new TopicPartitionGroup();
        topicPartitionGroup.setGroupNo(partitionGroup.getGroup());
        topicPartitionGroup.setPartitions(partitionGroup.getPartitions().toString());
        topicPartitionGroup.setElectType(partitionGroup.getElectType().type());
        topicPartitionGroup.setTopic(new Topic(partitionGroup.getTopic().getCode()));
        topicPartitionGroup.setNamespace(new Namespace(partitionGroup.getTopic().getNamespace()));
        topicPartitionGroup.setIsr(partitionGroup.getIsrs());
        topicPartitionGroup.setLeader(partitionGroup.getLeader());
        topicPartitionGroup.setLearners(partitionGroup.getLearners());
        topicPartitionGroup.setTerm(partitionGroup.getTerm());
        return topicPartitionGroup;
    }

    public static List<TopicPartitionGroup> convert(List<PartitionGroup> partitionGroups) {
        if (partitionGroups == null || partitionGroups.isEmpty()) {
            return Collections.emptyList();
        }
        List<TopicPartitionGroup> resultList = new ArrayList<>();
        partitionGroups.forEach(partitionGroup -> resultList.add(PartitionGroupConverter.convert(partitionGroup)));
        return resultList;
    }
}