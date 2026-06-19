package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class PlaceholderFeishuTenantAccessTokenFetcher implements FeishuTenantAccessTokenFetcher {
    @Override
    public FeishuTenantAccessToken fetch(FeishuProperties properties) {
        throw new BusinessException("FEISHU_OPENAPI_NOT_IMPLEMENTED", "真实飞书 tenant_access_token 获取接口尚未接入");
    }
}
