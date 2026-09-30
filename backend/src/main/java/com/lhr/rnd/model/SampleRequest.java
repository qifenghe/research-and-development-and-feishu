package com.lhr.rnd.model;

import com.lhr.rnd.domain.SampleStatus;

import java.time.LocalDateTime;

public record SampleRequest(
        String id,
        String sampleNo,
        String productName,
        String productType,
        String customerName,
        String specification,
        String applicationScenario,
        String flavorRequirement,
        String creatorName,
        SampleStatus status,
        LocalDateTime createdAt,
        Boolean referenceSampleProvided,
        String cutShape,
        String mainMaterialRequirement,
        String textureRequirement,
        String packagingRequirement,
        String storageRequirement,
        String customerTargetPrice,
        String monthlyDemand,
        String specialRequirements,
        String referenceMarketPrice,
        String additionalNotes
) {
}
