package com.jd.journalq.async;


import com.jd.journalq.common.domain.PartitionGroup;
import com.jd.journalq.model.domain.Broker;
import com.jd.journalq.model.domain.Subscribe;
import com.jd.journalq.service.BrokerRestUrlMappingService;
import com.jd.journalq.service.LeaderService;
import com.jd.journalq.util.AsyncHttpClient;
import com.jd.journalq.util.NullUtil;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service("brokerClusterMonitorQuery")
public class BrokerMonitorClusterQuery implements BrokerClusterQuery<Subscribe> {
    private Logger logger= LoggerFactory.getLogger(BrokerMonitorClusterQuery.class);
    @Autowired
    private LeaderService leaderService;

    @Autowired
    private BrokerRestUrlMappingService  urlMappingService;

    @Override
    public Future<Map<String, String>> asyncQueryOnBroker(Subscribe condition, RetrieveProvider<Subscribe> provider, String pathKey, String logKey) {
        List<Broker> brokers=leaderService.findLeaderBroker(condition.getTopic().getCode(),condition.getNamespace().getCode());
        String pathTemplate= urlMappingService.pathTemplate(pathKey);
        if (NullUtil.isEmpty(brokers)||NullUtil.isEmpty(pathTemplate)) {
            throw  new IllegalStateException("topic leader broker or rest path not found");
        }
        String path=provider.getPath(pathTemplate,null,(short)-1,condition);
        CountDownLatch latch = new CountDownLatch(brokers.size());
        Map<String/*request key*/,String/*response*/>  resultMap=new ConcurrentHashMap<>(brokers.size());
        String url;
        for(Broker b : brokers){
            //monitorUrl+ path with parameter
            url = urlMappingService.monitorUrl(b)+path;
            logger.info(String.format("start sync request,%s",url));
            AsyncHttpClient.AsyncRequest(new HttpGet(url), new AsyncHttpClient.ConcurrentHttpResponseHandler(latch,provider.getKey(b,null,(short)-1,condition),resultMap));
        }
      return new DefaultBrokerInfoFuture(latch,resultMap,logKey);
    }


    @Override
    public Future<Map<String, String>> asyncUpdateOnBroker(Subscribe condition, UpdateProvider<Subscribe> provider, String pathKey, String logKey) {
        List<Broker> brokers=leaderService.findLeaderBroker(condition.getTopic().getCode(),condition.getNamespace().getCode());
        String pathTemplate= urlMappingService.pathTemplate(pathKey);
        if (NullUtil.isEmpty(brokers)||NullUtil.isEmpty(pathTemplate)) {
            throw  new IllegalStateException("topic leader broker or rest path not found");
        }
        String path=provider.getPath(pathTemplate,null,(short)-1,condition);
        CountDownLatch latch = new CountDownLatch(brokers.size());
        Map<String/*request key*/,String/*response*/>  resultMap=new ConcurrentHashMap<>(brokers.size());
        String url;
        for(Broker b : brokers){
            //monitorUrl+ path with parameter
            url = urlMappingService.monitorUrl(b)+path;
            logger.info(String.format("start sync request,%s",url));
            AsyncHttpClient.AsyncRequest(provider.getRequest(url,null,(short)-1,condition), new AsyncHttpClient.ConcurrentHttpResponseHandler(latch,provider.getKey(b,null,(short)-1,condition),resultMap));
        }
        return new DefaultBrokerInfoFuture(latch,resultMap,logKey);
    }

    @Override
    public Future<Map<String, String>> asyncUpdateOnPartitionGroup(Subscribe condition, UpdateProvider<Subscribe> provider, String pathKey, String logKey) {
        List<Map.Entry<PartitionGroup,Broker>> partitionGroupLeaderBroker=leaderService.findPartitionGroupLeaderBrokerDetail(condition.getTopic().getCode(),condition.getNamespace().getCode());
        String pathTemplate= urlMappingService.pathTemplate(pathKey);
        if (NullUtil.isEmpty(partitionGroupLeaderBroker)||NullUtil.isEmpty(pathTemplate)) {
            throw  new IllegalArgumentException("partition group leader broker or rest path not found");
        }
        CountDownLatch latch = new CountDownLatch(partitionGroupLeaderBroker.size());
        Map<String/*request key*/,String/*response*/>  resultMap=new ConcurrentHashMap(partitionGroupLeaderBroker.size()*2);
        String url;
        // 遍历请求partition group
        for(Map.Entry<PartitionGroup,Broker> partitionGroupBrokerEntry : partitionGroupLeaderBroker){
            //monitorUrl+ path with parameter
            url = urlMappingService.monitorUrl(partitionGroupBrokerEntry.getValue())+provider.getPath(pathTemplate,partitionGroupBrokerEntry.getKey(),(short)-1,condition);
            logger.info(String.format("start sync request on partition group,%s",url));
            AsyncHttpClient.AsyncRequest(provider.getRequest(url,partitionGroupBrokerEntry.getKey(),(short)-1,condition), new AsyncHttpClient.ConcurrentHttpResponseHandler(latch,provider.getKey(partitionGroupBrokerEntry.getValue(),
                    partitionGroupBrokerEntry.getKey(),(short)-1,condition),resultMap));
        }
        return new DefaultBrokerInfoFuture(latch,resultMap,logKey);
    }

    @Override
    public Future<Map<String, String>> asyncUpdateOnPartition(Subscribe condition, UpdateProvider<Subscribe> provider, String pathKey, String logKey) {
        List<Map.Entry<PartitionGroup,Broker>> partitionGroupLeaderBroker=leaderService.findPartitionGroupLeaderBrokerDetail(condition.getTopic().getCode(),condition.getNamespace().getCode());
        String pathTemplate= urlMappingService.pathTemplate(pathKey);
        if (NullUtil.isEmpty(partitionGroupLeaderBroker)||NullUtil.isEmpty(pathTemplate)) {
            throw  new IllegalArgumentException("partition group leader broker or rest path not found");
        }
        CountDownLatch latch = new CountDownLatch(partitionGroupLeaderBroker.size());
        Map<String/*request key*/,String/*response*/>  resultMap=new ConcurrentHashMap(partitionGroupLeaderBroker.size()*2);
        String url;
        // 遍历请求partition group
        for(Map.Entry<PartitionGroup,Broker> partitionGroupBrokerEntry : partitionGroupLeaderBroker){
            PartitionGroup pg=partitionGroupBrokerEntry.getKey();
            //monitorUrl+ path with parameter
            for(Short partition:pg.getPartitions()) {
                url = urlMappingService.monitorUrl(partitionGroupBrokerEntry.getValue()) + provider.getPath(pathTemplate, partitionGroupBrokerEntry.getKey(),partition, condition);
                logger.info(String.format("start sync request on partition group,%s", url));
                AsyncHttpClient.AsyncRequest(provider.getRequest(url,partitionGroupBrokerEntry.getKey(),partition, condition), new AsyncHttpClient.ConcurrentHttpResponseHandler(latch, provider.getKey(partitionGroupBrokerEntry.getValue(),
                        partitionGroupBrokerEntry.getKey(), partition,condition), resultMap));
            }
        }
        return new DefaultBrokerInfoFuture(latch,resultMap,logKey);
    }

    @Override
    public Future<Map<String, String>> asyncQueryOnPartitionGroup(Subscribe condition, RetrieveProvider<Subscribe> provider, String pathKey, String logKey) {
        List<Map.Entry<PartitionGroup,Broker>> partitionGroupLeaderBroker=leaderService.findPartitionGroupLeaderBrokerDetail(condition.getTopic().getCode(),condition.getNamespace().getCode());
        String pathTemplate= urlMappingService.pathTemplate(pathKey);
        if (NullUtil.isEmpty(partitionGroupLeaderBroker)||NullUtil.isEmpty(pathTemplate)) {
            throw  new IllegalArgumentException("partition group leader broker or rest path not found");
        }
        CountDownLatch latch = new CountDownLatch(partitionGroupLeaderBroker.size());
        Map<String/*request key*/,String/*response*/>  resultMap=new ConcurrentHashMap(partitionGroupLeaderBroker.size()*2);
        String url;
        // 遍历请求partition group
        for(Map.Entry<PartitionGroup,Broker> partitionGroupBrokerEntry : partitionGroupLeaderBroker){
            //monitorUrl+ path with parameter
            url = urlMappingService.monitorUrl(partitionGroupBrokerEntry.getValue())+provider.getPath(pathTemplate,partitionGroupBrokerEntry.getKey(),(short)-1,condition);
            logger.info(String.format("start sync request on partition group,%s",url));
            AsyncHttpClient.AsyncRequest(new HttpGet(url), new AsyncHttpClient.ConcurrentHttpResponseHandler(latch,provider.getKey(partitionGroupBrokerEntry.getValue(),
                                                                                                                partitionGroupBrokerEntry.getKey(),(short)-1,condition),resultMap));
        }
        return new DefaultBrokerInfoFuture(latch,resultMap,logKey);
    }

    /**
     *
     *  block until all asyncQueryOnBroker back or timeout
     **/
    public Map<String,String> get(Future<Map<String,String>> resultFuture, long timeout, TimeUnit unit){
        try {
            return resultFuture.get(timeout,unit);
            //任一请求失败
        }catch (Exception e){
            throw new IllegalStateException(e);
        }
    }

}