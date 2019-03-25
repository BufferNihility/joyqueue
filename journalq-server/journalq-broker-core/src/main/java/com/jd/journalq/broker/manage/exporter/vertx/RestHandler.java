package com.jd.journalq.broker.manage.exporter.vertx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.journalq.common.monitor.RestResponse;
import com.jd.journalq.common.monitor.StringResponse;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * RestHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/10/16
 */
public class RestHandler implements Handler<RoutingContext> {

    protected static final Logger logger = LoggerFactory.getLogger(RestHandler.class);

    private HandlerInvoker handlerInvoker;

    public RestHandler(HandlerInvoker handlerInvoker) {
        this.handlerInvoker = handlerInvoker;
    }

    @Override
    public void handle(RoutingContext context) {
        RestResponse response = null;
        try {
            Object result = handlerInvoker.invoke(context);
            if (result instanceof RestResponse) {
                response = (RestResponse) result;
            } else {
                response = RestResponse.success(result);
            }
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                t = ((InvocationTargetException) t).getTargetException();
            }
            if (t instanceof IllegalArgumentException) {
                logger.debug("request IllegalArgumentException, path: {}, params: {}", context.request().path(), context.request().params(), t);
                response = RestResponse.paramError(t.getMessage());
            } else {
                logger.error("request exception, path: {}, params: {}", context.request().path(), context.request().params(), t);
                response = RestResponse.serverError(t.toString());
            }
        }

        HttpServerResponse httpResponse = context.response();
        if (response.getData() instanceof StringResponse) {
            for (Map.Entry<String, String> entry : ((StringResponse) response.getData()).getHeaders().entrySet()) {
                httpResponse.putHeader(entry.getKey(), entry.getValue());
            }
            httpResponse.end(((StringResponse) response.getData()).getBody());
        } else {
            httpResponse.putHeader("Content-Type", "application/json;charset=utf-8");
            httpResponse.end(JSON.toJSONString(response, SerializerFeature.PrettyFormat, SerializerFeature.DisableCircularReferenceDetect));
        }
    }
}