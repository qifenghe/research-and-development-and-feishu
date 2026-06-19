package com.lhr.rnd.service;

import com.lhr.rnd.model.UserAccount;

public record FeishuLoginResult(
        String feishuUserId,
        UserAccount user,
        String accessToken
) {
}
