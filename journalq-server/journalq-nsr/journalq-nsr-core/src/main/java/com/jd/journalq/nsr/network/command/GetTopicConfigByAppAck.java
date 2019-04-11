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
package com.jd.journalq.nsr.network.command;

import com.jd.journalq.domain.TopicConfig;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.network.transport.command.JMQPayload;

import java.util.Map;

/**
 * @author wylixiaobin
 * Date: 2019/1/27
 */
public class GetTopicConfigByAppAck extends JMQPayload {
    private Map<TopicName, TopicConfig> topicConfigs;

    public GetTopicConfigByAppAck topicConfigs(Map<TopicName, TopicConfig> topicConfigs){
        this.topicConfigs = topicConfigs;
        return this;
    }

    public Map<TopicName, TopicConfig> getTopicConfigs() {
        return topicConfigs;
    }

    @Override
    public int type() {
        return NsrCommandType.GET_TOPICCONFIGS_BY_APP_ACK;
    }
}
