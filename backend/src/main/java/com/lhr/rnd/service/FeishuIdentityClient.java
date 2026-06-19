package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public interface FeishuIdentityClient {
    String exchangeCodeForFeishuUserId(String code);

    FeishuSendResult sendNotification(FeishuNotificationEntity notification);
}
