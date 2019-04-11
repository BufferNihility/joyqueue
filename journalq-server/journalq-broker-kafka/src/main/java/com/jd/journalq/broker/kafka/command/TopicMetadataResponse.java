/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.journalq.broker.kafka.command;


import com.jd.journalq.broker.kafka.KafkaCommandType;
import com.jd.journalq.broker.kafka.model.KafkaBroker;
import com.jd.journalq.broker.kafka.model.KafkaTopicMetadata;

import java.util.List;

/**
 * Created by zhangkepeng on 16-7-29.
 */
public class TopicMetadataResponse extends KafkaRequestOrResponse {

    private List<KafkaTopicMetadata> topicMetadatas;
    private List<KafkaBroker> brokers;

    public TopicMetadataResponse(List<KafkaBroker> brokers, List<KafkaTopicMetadata> topicMetadatas) {
        this.brokers = brokers;
        this.topicMetadatas = topicMetadatas;
    }

    public List<KafkaTopicMetadata> getTopicMetadatas() {
        return topicMetadatas;
    }

    public void setTopicMetadatas(List<KafkaTopicMetadata> topicMetadatas) {
        this.topicMetadatas = topicMetadatas;
    }

    public List<KafkaBroker> getBrokers() {
        return brokers;
    }

    public void setBrokers(List<KafkaBroker> brokers) {
        this.brokers = brokers;
    }

    @Override
    public int type() {
        return KafkaCommandType.METADATA.getCode();
    }

    @Override
    public String toString() {
        StringBuilder responseStringBuilder = new StringBuilder();
        responseStringBuilder.append("Name: " + this.getClass().getSimpleName());
        return responseStringBuilder.toString();
    }
}
