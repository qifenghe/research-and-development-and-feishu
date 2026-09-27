package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.util.List;

public record PricingBasisResult(List<Material> materials, BigDecimal usageTotalKg,
        BigDecimal requisitionTotalKg, BigDecimal mainYieldPercent, Long referenceBags,
        Long referenceBoxes, BigDecimal remainingKg, List<Issue> issues) {
    public record Material(String id, BigDecimal usageKg, BigDecimal utilizationRate, BigDecimal requisitionKg) {}
    public record Issue(String path, String message) {}
}
