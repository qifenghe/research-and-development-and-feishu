package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record StoppedSampleProjectView(
        String projectId,
        String sampleNo,
        String productName,
        String productType,
        String customerName,
        String specification,
        String status,
        String lastVersionCode,
        String stoppedBy,
        String stopReason,
        LocalDateTime stoppedAt
) {
}
