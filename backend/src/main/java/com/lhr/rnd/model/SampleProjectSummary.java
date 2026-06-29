package com.lhr.rnd.model;

public record SampleProjectSummary(
        String id,
        String sampleNo,
        String productName,
        String productType,
        String customerName,
        String specification,
        String applicationScenario,
        String flavorRequirement,
        String status
) {
    public static SampleProjectSummary from(SampleProject project) {
        if (project == null) {
            return null;
        }
        return new SampleProjectSummary(
                project.id(),
                project.sampleNo(),
                project.productName(),
                project.productType(),
                project.customerName(),
                project.specification(),
                project.applicationScenario(),
                project.flavorRequirement(),
                project.status().name()
        );
    }
}
