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
package com.jd.journalq.client.internal.exception;

/**
 * ClientException
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/29
 */
public class ClientException extends RuntimeException {

    private int code;

    public ClientException() {
    }

    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientException(Throwable cause) {
        super(cause);
    }

    public ClientException(String error, int code) {
        super(error);
        this.code = code;
    }

    public ClientException(String error, int code, Throwable cause) {
        super(error, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}