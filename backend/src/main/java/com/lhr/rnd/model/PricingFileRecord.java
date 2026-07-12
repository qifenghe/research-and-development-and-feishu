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
        LocalDateTime generatedAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        String rejectionReason
) {
    public PricingFileRecord(
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
        this(id, versionId, sampleNo, productName, versionCode, pricingVersion, fileName, status, contentLength,
                generatedAt, null, null, null, null);
    }

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
                generatedAt,
                reviewedBy,
                reviewedAt,
                reviewComment,
                rejectionReason
        );
    }

    public PricingFileRecord reviewed(
            PricingFileStatus nextStatus,
            String reviewerName,
            LocalDateTime reviewedAt,
            String comment,
            String rejectionReason
    ) {
        return new PricingFileRecord(
                id, versionId, sampleNo, productName, versionCode, pricingVersion, fileName, nextStatus,
                contentLength, generatedAt, reviewerName, reviewedAt, comment, rejectionReason
        );
    }
}
