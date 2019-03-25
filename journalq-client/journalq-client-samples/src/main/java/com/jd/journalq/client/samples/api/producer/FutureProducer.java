package com.jd.journalq.client.samples.api.producer;

import com.jd.journalq.toolkit.network.IpUtil;
import io.openmessaging.Future;
import io.openmessaging.FutureListener;
import io.openmessaging.KeyValue;
import io.openmessaging.MessagingAccessPoint;
import io.openmessaging.OMS;
import io.openmessaging.jmq.JMQBuiltinKeys;
import io.openmessaging.jmq.domain.JMQProducerBuiltinKeys;
import io.openmessaging.message.Message;
import io.openmessaging.producer.Producer;
import io.openmessaging.producer.SendResult;

/**
 * FutureProducer
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/2/19
 */
public class FutureProducer {

    public static void main(String[] args) throws Exception {
        KeyValue keyValue = OMS.newKeyValue();
        keyValue.put(JMQBuiltinKeys.ACCOUNT_KEY, "test_token");
        keyValue.put(JMQProducerBuiltinKeys.TRANSACTION_TIMEOUT, 1000 * 10);

        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(String.format("oms:jmq://test_app@%s:50088/UNKNOWN", IpUtil.getLocalIp()), keyValue);

        Producer producer = messagingAccessPoint.createProducer();
        producer.start();

        Message message = producer.createMessage("test_topic_0", "body".getBytes());
        Future<SendResult> future = producer.sendAsync(message);

        future.addListener(new FutureListener<SendResult>() {
            @Override
            public void operationComplete(Future<SendResult> future) {
                System.out.println(future.get().messageId());
            }
        });

        System.in.read();
    }
}