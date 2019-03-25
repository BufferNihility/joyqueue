package com.jd.journalq.broker.security;

import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.common.domain.AppToken;
import com.jd.journalq.common.exception.JMQCode;
import com.jd.journalq.common.exception.JMQException;
import com.jd.journalq.common.response.BooleanResponse;
import com.jd.journalq.common.security.Authentication;

import com.jd.journalq.common.security.PasswordEncoder;
import com.jd.journalq.common.security.UserDetails;
import com.jd.journalq.toolkit.lang.Preconditions;
import com.jd.journalq.toolkit.time.SystemClock;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wylixiaobin
 * Date: 2019/1/21
 */
public class AppTokenAuthentication implements Authentication, BrokerContextAware {
    public static final String DEFAULT_ADMIN_USER="jmq";
    private String admin = DEFAULT_ADMIN_USER;
    private ClusterManager clusterManager;

    public AppTokenAuthentication() {
    }

    public AppTokenAuthentication(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    public AppTokenAuthentication(ClusterManager clusterManager, String admin) {
        this(clusterManager);
        this.admin = admin;
    }

    @Override
    public UserDetails getUser(String user) throws JMQException {
        return null;
    }

    @Override
    public PasswordEncoder getPasswordEncode() {
        return null;
    }

    @Override
    public BooleanResponse auth(String userName, String password) {
        AppToken appToken = clusterManager.getAppToken(userName, password);
        if (null == appToken) {
            return BooleanResponse.failed(JMQCode.CN_AUTHENTICATION_ERROR);
        }
        long now = SystemClock.now();
        if (now < appToken.getEffectiveTime().getTime() || now > appToken.getExpirationTime().getTime()) {
            return BooleanResponse.failed(JMQCode.CN_AUTHENTICATION_ERROR);
        }
        return BooleanResponse.success();
    }

    @Override
    public BooleanResponse auth(String userName, String password, boolean checkAdmin) {
        BooleanResponse response = auth(userName, password);
        if (response.isSuccess() && (checkAdmin?isAdmin(userName):true)) return response;
        return BooleanResponse.failed(JMQCode.CN_AUTHENTICATION_ERROR);
    }

    @Override
    public boolean isAdmin(String userName) {
        if (StringUtils.isBlank(userName)) return false;
        return userName.equals(admin);
    }

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.clusterManager = brokerContext.getClusterManager();
        Preconditions.checkArgument(clusterManager != null, "cluster manager can not be null");
        String adminUser = clusterManager.getConfig().getAdminUser();
        if(StringUtils.isNotBlank(adminUser))this.admin = adminUser;
    }
}