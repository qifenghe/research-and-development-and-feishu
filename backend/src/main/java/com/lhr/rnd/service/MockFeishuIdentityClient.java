package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public class MockFeishuIdentityClient implements FeishuIdentityClient {
    @Override
    public String exchangeCodeForFeishuUserId(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("FEISHU_OAUTH_CODE_REQUIRED", "飞书免登 code 不能为空");
        }
        if (code.startsWith("mock:")) {
            var feishuUserId = code.substring("mock:".length());
            if (!feishuUserId.isBlank()) {
                return feishuUserId;
            }
        }
        throw new BusinessException("FEISHU_OAUTH_CODE_UNSUPPORTED", "当前仅支持本地 mock 飞书免登 code");
    }

    @Override
    public FeishuSendResult sendNotification(FeishuNotificationEntity notification) {
        if (notification.getRecipientFeishuUserId().startsWith("fail_")) {
            return FeishuSendResult.failed("模拟飞书发送失败");
        }
        return FeishuSendResult.sent();
    }
}
