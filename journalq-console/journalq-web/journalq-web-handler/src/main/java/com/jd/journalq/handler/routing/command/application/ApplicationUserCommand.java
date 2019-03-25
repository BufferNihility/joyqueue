package com.jd.journalq.handler.routing.command.application;

import com.jd.journalq.handler.binder.annotation.GenericBody;
import com.jd.journalq.handler.binder.annotation.Page;
import com.jd.journalq.handler.binder.annotation.Path;
import com.jd.journalq.handler.error.ConfigException;
import com.jd.journalq.handler.error.ErrorCode;
import com.jd.journalq.handler.routing.command.CommandSupport;
import com.jd.journalq.handler.Constants;
import com.jd.journalq.model.domain.Application;
import com.jd.journalq.model.domain.ApplicationUser;
import com.jd.journalq.model.domain.Identity;
import com.jd.journalq.model.domain.User;
import com.jd.journalq.common.model.QPageQuery;
import com.jd.journalq.model.query.QUser;
import com.jd.journalq.service.UserService;
import com.jd.journalq.sync.SyncService;
import com.jd.laf.binding.annotation.Value;
import com.jd.laf.web.vertx.annotation.QueryParam;
import com.jd.laf.web.vertx.response.Response;
import com.jd.laf.web.vertx.response.Responses;

/**
 * Created by wangxiaofei1 on 2018/10/23.
 */
public class ApplicationUserCommand extends CommandSupport<ApplicationUser, UserService, QUser> {
    @QueryParam(Constants.APP_ID)
    protected Long appId;//应用id
    @Value(nullable = false)
    protected UserService userService;
    @Value(nullable = false)
    protected SyncService syncService;
    @Value(Constants.APPLICATION)
    protected Application application;

    @Path("add")
    public Response add(@GenericBody(type = GenericBody.BodyType.JSON,typeindex = 0) ApplicationUser applicationUser) throws Exception {
        //1. 参数检查
        if (applicationUser.getUser() == null) {
            throw new ConfigException(ErrorCode.BadRequest, "没有传入User参数!");
        }
        if(null == application) {
            throw new ConfigException(ErrorCode.BadRequest, "找不到此应用!");
        }
        //2. 查找/同步用户
        applicationUser.setApplication(application.identity());
        applicationUser.setCreateBy(operator);
        applicationUser.setUpdateBy(operator);
        Identity userIdentity = applicationUser.getUser();
        User user = userIdentity.getId() != null ? userService.findById(userIdentity.getId()) : userService.findByCode(userIdentity.getCode());
        if (user == null) {
            return Responses.error(404,"填写账号在此系统找不到");
//            UserInfo info = syncService.syncUser(user);
//            if (info == null) {
//                throw new ConfigException(ErrorCode.UserNotExists);
//            }
//            info.setUser(operator);
//            user = syncService.addOrUpdateUser(info);
        }
        applicationUser.setUser(user.identity());
        //3.保存appUser
        int count = userService.addAppUser(applicationUser);
        if (count <= 0) {
            throw new ConfigException(addErrorCode());
        }
        return Responses.success(applicationUser);
    }

    @Override
    @Path("search")
    public Response pageQuery(@Page(typeindex = 2)QPageQuery<QUser> qPageQuery) throws Exception {
        if (qPageQuery.getQuery()== null) {
            QUser qUser = new QUser();
            qUser.setAppId(appId);
            qPageQuery.setQuery(new QUser());
        } else {
            qPageQuery.getQuery().setAppId(appId);
        }
        return super.pageQuery(qPageQuery);
    }

}