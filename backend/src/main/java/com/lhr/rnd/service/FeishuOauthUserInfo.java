package com.lhr.rnd.service;

public record FeishuOauthUserInfo(
        String feishuUserId,
        String openId,
        String unionId
) {
}
