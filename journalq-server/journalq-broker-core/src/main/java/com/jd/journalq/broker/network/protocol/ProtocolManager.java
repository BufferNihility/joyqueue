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
package com.jd.journalq.broker.network.protocol;

import com.google.common.collect.Lists;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.network.protocol.Protocol;
import com.jd.journalq.network.protocol.ProtocolException;
import com.jd.journalq.network.protocol.ProtocolServer;
import com.jd.journalq.network.protocol.ProtocolService;
import com.jd.journalq.toolkit.lang.LifeCycle;
import com.jd.journalq.toolkit.service.Service;
import com.jd.laf.extension.ExtensionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 协议管理器
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/8/13
 */
public class ProtocolManager extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(ProtocolManager.class);

    private BrokerContext brokerContext;

    private List<Protocol> protocols = Lists.newLinkedList();
    private List<ProtocolService> protocolServices = Lists.newLinkedList();
    private List<ProtocolServer> protocolServers = Lists.newLinkedList();

    public ProtocolManager(BrokerContext brokerContext) {
        this.brokerContext = brokerContext;
        loadProtocols();
    }

    public List<ProtocolService> getProtocolServices() {
        return protocolServices;
    }

    public List<ProtocolServer> getProtocolServers() {
        return protocolServers;
    }

    public void register(Protocol protocol) {
        protocols.add(protocol);
        if (protocol instanceof ProtocolService) {
            protocolServices.add((ProtocolService) protocol);
        } else if (protocol instanceof ProtocolServer) {
            protocolServers.add((ProtocolServer) protocol);
        }
    }

    @Override
    protected void doStart() throws Exception {
        for (Protocol protocol : protocols) {
            try {
                initProtocol(protocol);
                logger.info("protocol {} is init", protocol.type());
            } catch (Exception e) {
                throw new ProtocolException(String.format("protocol %s init failed", protocol.type()), e);
            }
        }
    }

    @Override
    protected void doStop() {
        for (Protocol protocol : protocols) {
            try {
                stopProtocol(protocol);
            } catch (Exception e) {
                throw new ProtocolException(String.format("protocol %s stop failed", protocol.type()), e);
            }
        }
    }

    protected List<Protocol> loadProtocols() {
        List<Protocol> result = Lists.newLinkedList();
        List<ProtocolService> protocolServices = doGetProtocolServices();
        List<ProtocolServer> protocolServers = doGetProtocolServers();

        for (ProtocolService protocolService : protocolServices) {
            register(protocolService);
            result.add(protocolService);
        }
        for (ProtocolServer protocolServer : protocolServers) {
            register(protocolServer);
            result.add(protocolServer);
        }
        return result;
    }

    protected void initProtocol(Protocol protocol) throws Exception {
        if (protocol instanceof BrokerContextAware) {
            ((BrokerContextAware) protocol).setBrokerContext(brokerContext);
        }
        if (protocol instanceof LifeCycle) {
            ((LifeCycle) protocol).start();
        }
    }

    protected void stopProtocol(Protocol protocol) throws Exception {
        if (protocol instanceof LifeCycle) {
            ((LifeCycle) protocol).stop();
        }
    }

    protected List<ProtocolService> doGetProtocolServices() {
        return Lists.newArrayList(ExtensionManager.getOrLoadExtensions(ProtocolService.class));
    }

    protected List<ProtocolServer> doGetProtocolServers() {
        return Lists.newArrayList(ExtensionManager.getOrLoadExtensions(ProtocolServer.class));
    }
}