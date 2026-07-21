package com.lhr.rnd.model;

public record PricingReadyVersion(
        String versionId,
        String taskId,
        String sampleNo,
        String productName,
        String versionCode
) {
}
