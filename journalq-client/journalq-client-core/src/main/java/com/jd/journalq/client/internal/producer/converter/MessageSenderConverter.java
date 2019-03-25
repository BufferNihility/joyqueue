package com.jd.journalq.client.internal.producer.converter;

import com.google.common.collect.Lists;
import com.jd.journalq.client.internal.producer.domain.FeedbackData;
import com.jd.journalq.client.internal.producer.domain.FetchFeedbackData;
import com.jd.journalq.client.internal.producer.domain.ProduceMessage;
import com.jd.journalq.client.internal.producer.domain.SendBatchResultData;
import com.jd.journalq.client.internal.producer.domain.SendResult;
import com.jd.journalq.common.domain.QosLevel;
import com.jd.journalq.common.message.BrokerMessage;
import com.jd.journalq.common.network.command.FetchProduceFeedbackAck;
import com.jd.journalq.common.network.command.FetchProduceFeedbackAckData;
import com.jd.journalq.common.network.command.ProduceMessageAckData;
import com.jd.journalq.common.network.command.ProduceMessageAckItemData;
import com.jd.journalq.common.network.command.ProduceMessageData;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;

/**
 * MessageSenderConverter
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/28
 */
public class MessageSenderConverter {

    public static FetchFeedbackData convertToFetchFeedbackData(String topic, String app, FetchProduceFeedbackAck fetchProduceFeedbackAck) {
        FetchFeedbackData fetchFeedbackData = new FetchFeedbackData();
        if (CollectionUtils.isNotEmpty(fetchProduceFeedbackAck.getData())) {
            List<FeedbackData> data = Lists.newArrayListWithCapacity(fetchProduceFeedbackAck.getData().size());
            for (FetchProduceFeedbackAckData ackData : fetchProduceFeedbackAck.getData()) {
                data.add(new FeedbackData(ackData.getTopic(), ackData.getTxId(), ackData.getTransactionId()));
            }
            fetchFeedbackData.setData(data);
        }
        fetchFeedbackData.setCode(fetchProduceFeedbackAck.getCode());
        return fetchFeedbackData;
    }

    public static SendBatchResultData convertToBatchResultData(String topic, String app, ProduceMessageAckData produceMessageAckData) {
        SendBatchResultData sendBatchResultData = new SendBatchResultData();

        if (CollectionUtils.isNotEmpty(produceMessageAckData.getItem())) {
            List<SendResult> produceResultList = Lists.newArrayListWithCapacity(produceMessageAckData.getItem().size());
            for (ProduceMessageAckItemData produceMessageAckItemData : produceMessageAckData.getItem()) {
                SendResult produceResult = new SendResult(topic, produceMessageAckItemData.getPartition(), produceMessageAckItemData.getIndex(), produceMessageAckItemData.getStartTime());
                produceResultList.add(produceResult);
            }
            sendBatchResultData.setResult(produceResultList);
        }

        sendBatchResultData.setCode(produceMessageAckData.getCode());
        return sendBatchResultData;
    }

    public static ProduceMessageData convertToProduceMessageData(String topic, String app, String txId, List<ProduceMessage> messages, QosLevel qosLevel, long timeout,
                                                                 boolean compress, int compressThreshold, String compressType) {
        List<BrokerMessage> brokerMessages = ProduceMessageConverter.convertToBrokerMessages(topic, app, messages, compress, compressThreshold, compressType);
        ProduceMessageData produceMessageData = new ProduceMessageData();
        produceMessageData.setQosLevel(qosLevel);
        produceMessageData.setMessages(brokerMessages);
        produceMessageData.setTxId(txId);
        produceMessageData.setTimeout((int) timeout);
        return produceMessageData;
    }
}