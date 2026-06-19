package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public class OpenApiFeishuIdentityClient implements FeishuIdentityClient {
    private final FeishuProperties properties;
    private final FeishuTenantAccessTokenService tenantAccessTokenService;
    private final FeishuOauthUserInfoFetcher oauthUserInfoFetcher;
    private final FeishuMessageSender messageSender;

    public OpenApiFeishuIdentityClient(
            FeishuProperties properties,
            FeishuTenantAccessTokenService tenantAccessTokenService,
            FeishuOauthUserInfoFetcher oauthUserInfoFetcher,
            FeishuMessageSender messageSender
    ) {
        this.properties = properties;
        this.tenantAccessTokenService = tenantAccessTokenService;
        this.oauthUserInfoFetcher = oauthUserInfoFetcher;
        this.messageSender = messageSender;
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
        var tenantAccessToken = tenantAccessTokenService.currentToken();
        return messageSender.send(properties, tenantAccessToken, notification);
    }
}
