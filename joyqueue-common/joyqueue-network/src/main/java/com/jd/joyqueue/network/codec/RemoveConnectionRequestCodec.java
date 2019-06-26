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
package com.jd.joyqueue.network.codec;

import com.jd.joyqueue.network.command.JoyQueueCommandType;
import com.jd.joyqueue.network.command.RemoveConnectionRequest;
import com.jd.joyqueue.network.transport.codec.JoyQueueHeader;
import com.jd.joyqueue.network.transport.codec.PayloadCodec;
import com.jd.joyqueue.network.transport.command.Type;
import io.netty.buffer.ByteBuf;

/**
 * RemoveConnectionRequestCodec
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/30
 */
public class RemoveConnectionRequestCodec implements PayloadCodec<JoyQueueHeader, RemoveConnectionRequest>, Type {

    @Override
    public RemoveConnectionRequest decode(JoyQueueHeader header, ByteBuf buffer) throws Exception {
        return new RemoveConnectionRequest();
    }

    @Override
    public void encode(RemoveConnectionRequest payload, ByteBuf buffer) throws Exception {
    }

    @Override
    public int type() {
        return JoyQueueCommandType.REMOVE_CONNECTION_REQUEST.getCode();
    }
}