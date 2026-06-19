package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record FeishuNotification(
        String id,
        String businessType,
        String businessId,
        String recipientUserId,
        String recipientFeishuUserId,
        String templateKey,
        String title,
        String content,
        String status,
        int sendAttempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
}
