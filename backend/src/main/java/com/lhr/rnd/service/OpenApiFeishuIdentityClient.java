package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public class OpenApiFeishuIdentityClient implements FeishuIdentityClient {
    private final FeishuProperties properties;

    public OpenApiFeishuIdentityClient(FeishuProperties properties) {
        this.properties = properties;
    }

    @Override
    public String exchangeCodeForFeishuUserId(String code) {
        ensureReady();
        throw new BusinessException("FEISHU_OPENAPI_NOT_IMPLEMENTED", "真实飞书免登接口尚未接入");
    }

    @Override
    public FeishuSendResult sendNotification(FeishuNotificationEntity notification) {
        if (!properties.readyForOpenApi()) {
            return FeishuSendResult.failed("飞书 OpenAPI 配置未完成");
        }
        return FeishuSendResult.failed("真实飞书消息发送接口尚未接入");
    }

    private void ensureReady() {
        if (!properties.readyForOpenApi()) {
            throw new BusinessException("FEISHU_OPENAPI_CONFIG_INCOMPLETE", "飞书 OpenAPI 配置未完成");
        }
    }
}
