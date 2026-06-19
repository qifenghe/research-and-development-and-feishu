package com.lhr.rnd.service;

public interface FeishuOauthUserInfoFetcher {
    FeishuOauthUserInfo fetch(FeishuProperties properties, String tenantAccessToken, String code);
}
