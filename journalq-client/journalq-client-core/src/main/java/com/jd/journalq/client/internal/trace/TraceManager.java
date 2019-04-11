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
package com.jd.journalq.client.internal.trace;

import com.jd.journalq.client.internal.Plugins;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TraceManager
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/1/3
 */
public class TraceManager {

    private static List<Trace> traces;

    static {
        traces = loadTraces();
    }

    public static Trace getTrace(String type) {
        return Plugins.TRACE.get(type);
    }

    public static List<Trace> getTraces() {
        return traces;
    }

    protected static List<Trace> loadTraces() {
        Iterable<Trace> iterable = Plugins.TRACE.extensions();
        if (iterable != null) {
            return Arrays.asList(StreamSupport.stream(iterable.spliterator(), false).toArray(Trace[]::new));
        }
        return Collections.emptyList();
    }


}