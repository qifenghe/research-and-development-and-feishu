package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record FinanceNotification(
        String id,
        String pricingFileId,
        String recipientName,
        String remark,
        FinanceNotificationStatus status,
        LocalDateTime notifiedAt
) {
}
