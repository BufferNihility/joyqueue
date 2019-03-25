package com.jd.journalq.api.Impl;


import com.jd.journalq.api.OpenAPIService;
import com.jd.journalq.common.model.PageResult;
import com.jd.journalq.common.model.Pagination;
import com.jd.journalq.common.model.QPageQuery;
import com.jd.journalq.common.monitor.PartitionAckMonitorInfo;
import com.jd.journalq.common.monitor.PendingMonitorInfo;
import com.jd.journalq.convert.CodeConverter;
import com.jd.journalq.exception.ServiceException;
import com.jd.journalq.service.*;
import com.jd.journalq.sync.ApplicationInfo;
import com.jd.journalq.sync.SyncService;
import com.jd.journalq.sync.UserInfo;
import com.jd.journalq.util.LocalSession;
import com.jd.journalq.util.NullUtil;
import com.jd.journalq.model.domain.*;
import com.jd.journalq.model.query.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.jd.journalq.exception.ServiceException.BAD_REQUEST;
import static com.jd.journalq.exception.ServiceException.INTERNAL_SERVER_ERROR;

@Service("openAPIService")
public class OpenAPIServiceImpl implements OpenAPIService {

    @Autowired
    private TopicService topicService;

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private ProducerService producerService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private BrokerGroupService brokerGroupService;

    @Autowired
    private BrokerService brokerService;

    @Autowired
    private ConsumeOffsetService consumeOffsetService;

    @Autowired
    private BrokerMonitorService brokerMonitorService;

    @Autowired
    private LeaderService leaderService;

    @Autowired
    private  ApplicationTokenService  applicationTokenService;
    @Autowired
    private  ApplicationUserService  applicationUserService;
    private Random random=new Random();
    private final static long MINUTES_MS=60*1000;




    @Override
    public PageResult<TopicPubSub> findTopicPubSubInfo(Pagination pagination) throws Exception{
        QPageQuery<QTopic> qPageQuery=new QPageQuery();
        qPageQuery.setQuery(new QTopic()); //empty
        qPageQuery.setPagination(pagination);
        PageResult<Topic> topicPageResult=topicService.findByQuery(qPageQuery);
        List<Topic>  topics= topicPageResult.getResult();
        List<TopicPubSub> pubSubs=new ArrayList(topics.size());
        for(Topic topic:topics){
            pubSubs.add(findTopicPubsub(topic));
        }
        PageResult<TopicPubSub> topicPubSubPageResult=new PageResult<>();
        topicPubSubPageResult.setPagination(topicPageResult.getPagination());
        topicPubSubPageResult.setResult(pubSubs);
        return topicPubSubPageResult;
    }


    /**
     * @return  topic pub/sub info
     **/
    TopicPubSub findTopicPubsub(Topic topic) throws Exception{
        QConsumer qConsumer=new QConsumer();
        QProducer qProducer=new QProducer();
        qConsumer.setTopic(topic);
        qProducer.setTopic(topic);
        List<Consumer> consumers=consumerService.findByQuery(qConsumer);
        List<Producer> producers=producerService.findByQuery(qProducer);
        TopicPubSub pubSub=new TopicPubSub();

        List<String> ips=new ArrayList<>();
        List<Broker> brokers=leaderService.findLeaderBroker(topic.getCode(),topic.getNamespace().getCode());
        if(!NullUtil.isEmpty(brokers)){
            String iport;
            for(Broker b:brokers){
                iport=b.getIp()+":"+b.getPort();
                ips.add(iport);
            }
        }
        SlimTopic slimTopic=new SlimTopic();
        slimTopic.setIps(ips);
        slimTopic.setCode(topic.getCode());
        pubSub.setTopic(slimTopic);
        List<String> apps=new ArrayList<>();
        Identity identity;
        for(Consumer consumer:consumers){
            identity=consumer.getApp();
            if(!NullUtil.isEmpty(identity)) {
                apps.add(String.valueOf(identity.getCode()));
            }
        }
        apps.clear();
        for(Producer producer:producers){
            identity=producer.getApp();
            if(!NullUtil.isEmpty(identity)) {
                apps.add(String.valueOf(identity.getCode()));
            }
        }
        pubSub.setConsumers(appsToApplication(apps));
        pubSub.setProducers(appsToApplication(apps));
        return pubSub;
    }
    @Override
    public TopicPubSub findTopicPubSubInfo(String topic, String namespace) throws Exception{
        Topic topiC=new Topic();
        topiC.setCode(topic);
        topiC.setNamespace(new Namespace());
        topiC.getNamespace().setCode(namespace);
        return findTopicPubsub(topiC);
    }
    @Override
    public List<Consumer>  queryConsumerTopicByApp(String app) throws Exception {
        QConsumer qConsumer = new QConsumer();
        qConsumer.setReferer(app);
        return consumerService.findByQuery(qConsumer);
    }

    @Override
    public List<Consumer> findConsumers(String topic, String namespace) throws Exception {
        Topic topiC=new Topic(topic);
        topiC.setNamespace(new Namespace(namespace));
        QConsumer qConsumer=new QConsumer();
        qConsumer.setNamespace(namespace);
        qConsumer.setTopic(topiC);
        List<Consumer> consumers=consumerService.findByQuery(qConsumer);
        return consumers;
    }

    @Override
    public List<Producer> findProducers(String topic, String namespace) throws Exception{
        Topic topiC=new Topic(topic);
        topiC.setNamespace(new Namespace(namespace));
        QProducer qProducer=new QProducer();
        qProducer.setTopic(topiC);
        List<Producer> producers=producerService.findByQuery(qProducer);
        return producers;
    }

    @Override
    public Producer publish(Producer producer) throws Exception {
        Topic topic=  topicService.findByCode(producer.getNamespace().getCode()  ,producer.getTopic().getCode());
        Application application=applicationService.findByCode(producer.getApp().getCode());
        if(NullUtil.isEmpty(topic)||NullUtil.isEmpty(application)){
            throw new ServiceException(BAD_REQUEST,String.format("topic %s or app %s not exist!",producer.getTopic().getCode(),producer.getApp().getCode()));
        }
        producer.setTopic(topic);
        producer.setApp(new Identity(producer.getApp().getCode()));
        producerService.add(producer);
        return producerService.findByTopicAppGroup(producer.getNamespace().getCode(),producer.getTopic().getCode(),producer.getApp().getCode());
    }

    @Override
    public Consumer subscribe(Consumer consumer) throws Exception{
        Topic topic=  topicService.findByCode(consumer.getNamespace().getCode()  ,consumer.getTopic().getCode());
        Application application=applicationService.findByCode(consumer.getApp().getCode());
        if(NullUtil.isEmpty(topic)||NullUtil.isEmpty(application)){
            throw new ServiceException(BAD_REQUEST,String.format("topic %s or app %s not exist!",consumer.getTopic().getCode(),consumer.getApp().getCode()));
        }
        consumer.setTopic(topic);
        consumer.setNamespace(consumer.getNamespace());
        consumer.setApp(consumer.getApp());
        consumerService.add(consumer);
        return consumerService.findByTopicAppGroup(consumer.getNamespace().getCode(),consumer.getTopic().getCode(),consumer.getApp().getCode(),consumer.getSubscribeGroup());
    }

    @Override
    public boolean unPublish(Producer producer) throws Exception{
        List<Producer> producers= findProducers(producer.getTopic().getCode(),producer.getNamespace().getCode());
        List<Consumer> consumers= findConsumers(producer.getTopic().getCode(),producer.getNamespace().getCode());
        if(NullUtil.isEmpty(producers)||(producers.size()==1&&consumers.size()>0)){
            throw new ServiceException(BAD_REQUEST,String.format("no subscribe or please unSubscribe all the consumers of topic %s before cancel publish",
                    producer.getTopic().getCode()));
        }
        Producer p=findProducer(producers,producer.getApp().getCode());
        if(NullUtil.isEmpty(p)) throw new ServiceException(BAD_REQUEST,String.format(" %s haven't publish to the topic %s ",
                producer.getApp().getCode(),CodeConverter.convertTopic(producer.getNamespace(),producer.getTopic()).getFullName()));

        return producerService.delete(p)>0?true:false;
    }

    @Override
    public Consumer uniqueSubscribe(Consumer consumer) throws Exception{
        String namespace = consumer.getNamespace()==null?null:consumer.getNamespace().getCode();
        Topic topic=  topicService.findByCode(namespace,consumer.getTopic().getCode());
        Application application=applicationService.findByCode(consumer.getApp().getCode());
        if(NullUtil.isEmpty(topic)||NullUtil.isEmpty(application)){
            throw new ServiceException(BAD_REQUEST,String.format("topic %s or app %s not exist!",consumer.getTopic().getCode(),consumer.getApp().getCode()));
        }
        User user = LocalSession.getSession().getUser();
        ApplicationUser applicationUser = applicationUserService.findByUserApp(user.getCode(),consumer.getApp().getCode());
        if (NullUtil.isEmpty(applicationUser)) {
            throw new ServiceException(BAD_REQUEST,String.format("user %s app %s no permission!",user.getCode(),consumer.getApp().getCode()));
        }
        Consumer exist = consumerService.findByTopicAppGroup(namespace,consumer.getTopic().getCode(),consumer.getApp().getCode(),consumer.getSubscribeGroup());
        if (NullUtil.isNotEmpty(exist) && NullUtil.isNotEmpty(exist.getSubscribeGroup())) {
            return exist;
        }
        int group=random.nextInt((int)MINUTES_MS);
        consumer.setSubscribeGroup(String.valueOf(group));
        consumer.setTopic(topic);
        consumer.setNamespace(consumer.getNamespace());
        consumer.setApp(consumer.getApp());
        consumerService.add(consumer);
        return consumerService.findByTopicAppGroup(namespace,consumer.getTopic().getCode(),consumer.getApp().getCode(),consumer.getSubscribeGroup());
    }

    /**
     *  find the app producer
     *
     **/
    Producer findProducer(List<Producer> producers,String app){
        for(Producer p:producers){
            if(p.getApp().getCode().equals(app)){
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean unSubscribe(Consumer consumer) throws Exception{
        Consumer c=consumerService.findByTopicAppGroup(consumer.getNamespace().getCode(),consumer.getTopic().getCode(),
                consumer.getApp().getCode(),consumer.getSubscribeGroup());
        if(NullUtil.isEmpty(c)) throw new ServiceException(BAD_REQUEST,String.format(" %s haven't subscribe to the topic %s ",
                CodeConverter.convertApp(new Identity(consumer.getApp().getCode()),consumer.getSubscribeGroup()),CodeConverter.convertTopic(consumer.getNamespace(),consumer.getTopic()).getFullName()));
        // check pending message

        return consumerService.delete(c)>0?true:false;
    }

    @Override
    public Application syncApplication(Application application) throws Exception{
        User user=syncService.addOrUpdateUser(new UserInfo(application.getErp()));
        ApplicationInfo info = syncService.syncApp(application);
        if (NullUtil.isEmpty(info)||NullUtil.isEmpty(user)) {
            throw new ServiceException(BAD_REQUEST,"sync application failed or illegal erp "+application.getErp());
        }
        info.setUser(new Identity(user));
        syncService.addOrUpdateApp(info);
        return applicationService.findByCode(info.getCode());
    }

    @Override
    public boolean delApplication(Application application) throws Exception {
        QConsumer qconsumer=new QConsumer();
        QProducer qProducer=new QProducer();
        Identity app=new Identity();
        app.setId(application.getId());
        app.setCode(application.getCode());
        qconsumer.setApp(app);
        qProducer.setApp(app);
        if(!NullUtil.isEmpty(consumerService.findByQuery(qconsumer))||!NullUtil.isEmpty(producerService.findByQuery(qProducer)))
            throw new ServiceException(BAD_REQUEST,"please unSubscribe/Publish  all  topics you have !");
        application=applicationService.findByCode(application.getCode());
        return applicationService.delete(application)>0?true:false;
    }

    @Override
    public Topic createTopic(Topic topic, QBrokerGroup brokerGroup,Identity operator) throws Exception{
//        topic.setElectType(PartitionGroup.ElectType.raft);
        List<Broker> brokers=allocateBrokers(topic,brokerGroup);
        topic.setBrokers(brokers);
        topicService.addWithBrokerGroup(topic,topic.getBrokerGroup(),topic.getBrokers(),operator);
        return topicService.findById(topic.getId());
    }

    /**
     * Random allocate broker group and broker  for topic
     **/
    List<Broker> allocateBrokers(Topic topic,QBrokerGroup brokerGroup) throws Exception{
        List<BrokerGroup> allBrokerGroup= brokerGroupService.findAll(brokerGroup);
        Random random=new Random();
        List<Broker> brokers;
        int maxTries=10;
        do {
            int index = (int) (random.nextDouble() * allBrokerGroup.size());
            BrokerGroup b = allBrokerGroup.get(index);
            QBroker qBroker = new QBroker();
            qBroker.setBrokerGroupId(b.getId());
            QPageQuery<QBroker> qBrokerQPageQuery = new QPageQuery<>();
            qBrokerQPageQuery.setQuery(qBroker);
            Pagination pagination = new Pagination();
            pagination.setSize(Integer.MAX_VALUE);
            qBrokerQPageQuery.setPagination(pagination);
            PageResult<Broker> brokerPageResult = brokerService.findByQuery(qBrokerQPageQuery);
            brokers=brokerPageResult.getResult();
            // to do if brokers too many
            topic.setBrokerGroup(b); // broker group
        }while(maxTries-->0&&NullUtil.isEmpty(brokers));
        return brokers;
    }

    @Override
    public List<PartitionAckMonitorInfo> findOffsets(Subscribe subscribe) {
        subscribe.setType(SubscribeType.CONSUMER);
        isLegalSubscribe(subscribe);
        return consumeOffsetService.offsets(subscribe);
    }

    @Override
    public boolean resetOffset(Subscribe subscribe, short partition, long offset) {
        subscribe.setType(SubscribeType.CONSUMER);
        isLegalSubscribe(subscribe);
        return consumeOffsetService.resetOffset(subscribe,partition,offset);
    }

    @Override
    public List<PartitionAckMonitorInfo> timeOffset(Subscribe subscribe, long timeMs) {
        return consumeOffsetService.timeOffset(subscribe,timeMs);
    }

    @Override
    public boolean resetOffset(Subscribe subscribe, long timeMs) {
        subscribe.setType(SubscribeType.CONSUMER);
        isLegalSubscribe(subscribe);
        return consumeOffsetService.resetOffset(subscribe,timeMs);
    }

    @Override
    public boolean resetOffset(Subscribe subscribe, List<PartitionOffset> offsets) {
        subscribe.setType(SubscribeType.CONSUMER);
        isLegalSubscribe(subscribe);
        return consumeOffsetService.resetOffset(subscribe,offsets);
    }

    @Override
    public PendingMonitorInfo pending(Subscribe subscribe) {
        subscribe.setType(SubscribeType.CONSUMER);
        isLegalSubscribe(subscribe);
        BrokerMonitorRecord record= brokerMonitorService.find(subscribe,true);
        if(NullUtil.isEmpty(record)||NullUtil.isEmpty(record.getPending())){
            throw new ServiceException(INTERNAL_SERVER_ERROR,"data not found");
        }
        return record.getPending();
    }

    @Override
    public int queryPartitionByTopic(String namespaceCode,String topicCode) throws Exception {
        Topic topic = topicService.findByCode(namespaceCode,topicCode);
        return topic.getPartitions();
    }


    /**
     *
     * @param apps  can't be null
     *
     **/
    List<SlimApplication> appsToApplication(List<String> apps){
        if(NullUtil.isEmpty(apps)) return null;
        List<Application> applications= applicationService.findByCodes(apps);
        List<SlimApplication>  slimApplications=new ArrayList<>();
        SlimApplication slimApplication;
        for(Application a:applications){
            slimApplication=new SlimApplication();
            slimApplication.setCode(a.getCode());
            slimApplication.setOwner(a.getOwner());
            slimApplication.setDepartment(a.getDepartment());
            slimApplications.add(slimApplication);
        }
        return slimApplications;
    }


    @Override
    public List<ApplicationToken> add(ApplicationToken token) {
        String app=token.getApplication().getCode();
        Application application= applicationService.findByCode(app);
        if(NullUtil.isEmpty(application)||application.getStatus()==BaseModel.DELETED){
            throw  new ServiceException(BAD_REQUEST,"app not exist");
        }
        token.setApplication(new Identity(application));
        try {
            applicationTokenService.add(token);
            return tokens(app);
        }catch (Exception e){
            throw new ServiceException(INTERNAL_SERVER_ERROR,e.getMessage());
        }
    }

    @Override
    public List<ApplicationToken> tokens(String app) {
        QApplicationToken qApplicationToken=new QApplicationToken(new Identity(app),null);
        try {
            return  applicationTokenService.findByQuery(qApplicationToken);
        }catch (Exception e){
            throw new ServiceException(INTERNAL_SERVER_ERROR,e.getMessage());
        }

    }

    /**
     *
     *  Check the subscription legal or not
     *
     *  @return  true if exist
     *
     **/
    private boolean isLegalSubscribe(Subscribe subscribe){
        if(subscribe.getType()==SubscribeType.CONSUMER) {
            Consumer c = consumerService.findByTopicAppGroup(subscribe.getNamespace().getCode(), subscribe.getTopic().getCode(),
                    subscribe.getApp().getCode(), subscribe.getSubscribeGroup());
            if (NullUtil.isEmpty(c))
                throw new ServiceException(BAD_REQUEST, String.format(" %s haven't subscribe the topic %s ",
                        CodeConverter.convertApp(subscribe.getApp(), subscribe.getSubscribeGroup()), CodeConverter.convertTopic(subscribe.getNamespace(), subscribe.getTopic()).getFullName()));
        }else{
            Producer producer=producerService.findByTopicAppGroup(subscribe.getNamespace().getCode(), subscribe.getTopic().getCode(),subscribe.getApp().getCode());
            if(NullUtil.isEmpty(producer)){
                throw new ServiceException(BAD_REQUEST, String.format(" %s haven't publish the topic %s ",
                        subscribe.getApp().getCode(), CodeConverter.convertTopic(subscribe.getNamespace(), subscribe.getTopic()).getFullName()));
            }
        }
        return true;
    }







}