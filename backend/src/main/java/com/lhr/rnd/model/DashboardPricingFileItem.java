package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record DashboardPricingFileItem(
        String pricingFileId,
        String sampleNo,
        String productName,
        String versionCode,
        String pricingVersion,
        String status,
        LocalDateTime generatedAt
) {
}
