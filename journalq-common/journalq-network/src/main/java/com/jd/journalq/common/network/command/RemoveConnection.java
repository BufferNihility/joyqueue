package com.jd.journalq.common.network.command;

import com.jd.journalq.common.network.transport.command.JMQPayload;

/**
 * RemoveConnection
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/30
 */
public class RemoveConnection extends JMQPayload {

    @Override
    public int type() {
        return JMQCommandType.REMOVE_CONNECTION.getCode();
    }
}