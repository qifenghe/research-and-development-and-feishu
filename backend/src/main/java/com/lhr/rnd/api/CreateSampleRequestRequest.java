package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSampleRequestRequest(
        @NotBlank String productName,
        @NotBlank String productType,
        @NotBlank String customerName,
        @NotBlank String specification,
        @NotBlank String applicationScenario,
        @NotBlank String flavorRequirement,
        @NotBlank(message="请填写销售申请人") String creatorName,
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
    public com.lhr.rnd.service.SampleWorkflowService.CreateSampleRequestCommand command() {
        return new com.lhr.rnd.service.SampleWorkflowService.CreateSampleRequestCommand(
                productName, productType, customerName, specification, applicationScenario, flavorRequirement,
                creatorName, referenceSampleProvided, cutShape, mainMaterialRequirement, textureRequirement,
                packagingRequirement, storageRequirement, customerTargetPrice, monthlyDemand, specialRequirements,
                referenceMarketPrice, additionalNotes);
    }
}
