package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public class OpenApiFeishuIdentityClient implements FeishuIdentityClient {
    private final FeishuProperties properties;
    private final FeishuTenantAccessTokenService tenantAccessTokenService;

    public OpenApiFeishuIdentityClient(
            FeishuProperties properties,
            FeishuTenantAccessTokenService tenantAccessTokenService
    ) {
        this.properties = properties;
        this.tenantAccessTokenService = tenantAccessTokenService;
    }

    @Override
    public String exchangeCodeForFeishuUserId(String code) {
        tenantAccessTokenService.currentToken();
        throw new BusinessException("FEISHU_OPENAPI_NOT_IMPLEMENTED", "真实飞书免登接口尚未接入");
    }

    @Override
    public FeishuSendResult sendNotification(FeishuNotificationEntity notification) {
        if (!properties.readyForOpenApi()) {
            return FeishuSendResult.failed("飞书 OpenAPI 配置未完成");
        }
        tenantAccessTokenService.currentToken();
        return FeishuSendResult.failed("真实飞书消息发送接口尚未接入");
    }
}
