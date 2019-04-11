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
package com.jd.journalq.datasource;


import com.jd.laf.extension.Type;

/**
 * 数据源构造器
 */
public interface DataSourceBuilder extends Type {

    /**
     * 创建连接池
     *
     * @param config 连接池配置
     * @return 连接池
     */
    XDataSource build(DataSourceConfig config);

}
