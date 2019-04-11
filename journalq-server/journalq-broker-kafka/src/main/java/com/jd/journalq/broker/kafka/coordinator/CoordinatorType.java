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
package com.jd.journalq.broker.kafka.coordinator;

/**
 * CoordinatorType
 *
 * @author luoruiheng
 * @since 1/9/18
 */
public enum CoordinatorType {

    GROUP((byte) 0),

    TRANSACTION((byte) 1),

    ;

    private byte code;

    CoordinatorType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static CoordinatorType valueOf(byte code) {
        switch (code) {
            case 0:
                return GROUP;
            case 1:
                return TRANSACTION;
            default:
                throw new IllegalArgumentException("unknown coordinator type received: " + code);
        }
    }
}