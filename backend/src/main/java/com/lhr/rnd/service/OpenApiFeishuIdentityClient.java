package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public class OpenApiFeishuIdentityClient implements FeishuIdentityClient {
    private final FeishuProperties properties;
    private final FeishuTenantAccessTokenService tenantAccessTokenService;
    private final FeishuOauthUserInfoFetcher oauthUserInfoFetcher;

    public OpenApiFeishuIdentityClient(
            FeishuProperties properties,
            FeishuTenantAccessTokenService tenantAccessTokenService,
            FeishuOauthUserInfoFetcher oauthUserInfoFetcher
    ) {
        this.properties = properties;
        this.tenantAccessTokenService = tenantAccessTokenService;
        this.oauthUserInfoFetcher = oauthUserInfoFetcher;
    }

    @Override
    public String exchangeCodeForFeishuUserId(String code) {
        var tenantAccessToken = tenantAccessTokenService.currentToken();
        return oauthUserInfoFetcher.fetch(properties, tenantAccessToken, code).feishuUserId();
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
