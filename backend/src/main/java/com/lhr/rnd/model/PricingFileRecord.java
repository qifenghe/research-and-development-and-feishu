package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record PricingFileRecord(
        String id,
        String versionId,
        String sampleNo,
        String productName,
        String versionCode,
        String pricingVersion,
        String fileName,
        PricingFileStatus status,
        long contentLength,
        LocalDateTime generatedAt
) {
    public PricingFileRecord withStatus(PricingFileStatus nextStatus) {
        return new PricingFileRecord(
                id,
                versionId,
                sampleNo,
                productName,
                versionCode,
                pricingVersion,
                fileName,
                nextStatus,
                contentLength,
                generatedAt
        );
    }
}
