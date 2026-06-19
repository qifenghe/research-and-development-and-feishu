package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;

public interface FeishuMessageSender {
    FeishuSendResult send(FeishuProperties properties, String tenantAccessToken, FeishuNotificationEntity notification);
}
