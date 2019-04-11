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
package com.jd.journalq.convert;

import com.jd.journalq.model.domain.Config;

/**
 * Created by wangxiaofei1 on 2018/12/27.
 */
public class NsrConfigConverter extends Converter<Config, com.jd.journalq.domain.Config> {

    @Override
    protected com.jd.journalq.domain.Config forward(Config config) {
        com.jd.journalq.domain.Config nsrConfig = new com.jd.journalq.domain.Config();
        nsrConfig.setGroup(config.getGroup());
        nsrConfig.setKey(config.getKey());
        nsrConfig.setValue(config.getValue());
        return nsrConfig;
    }

    @Override
    protected Config backward(com.jd.journalq.domain.Config nsrConfig) {
        Config config = new Config();
        config.setId(nsrConfig.getId());
        config.setGroup(nsrConfig.getGroup());
        config.setKey(nsrConfig.getKey());
        config.setValue(nsrConfig.getValue());
        return config;
    }
}
