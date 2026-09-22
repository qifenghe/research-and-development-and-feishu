package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.util.List;

/** Read-only values bound to one saved plan/revision, never planned targets or editable form guesses. */
public record ProcessExportView(String productName, String sourceLabel, ProcessPlan snapshot,
        BigDecimal externalInputKg, BigDecimal mainYieldPercent, List<Ingredient> ingredients,
        List<Major> majors, FinishedQuantity finishedQuantity, List<PricingPackagingItem> packaging,
        List<Issue> issues, Metadata metadata) {
    public record Metadata(String specification, String compiledBy, String date) {}
    public ProcessExportView withMetadata(Metadata metadata) {
        return new ProcessExportView(productName, sourceLabel, snapshot, externalInputKg, mainYieldPercent, ingredients,
                majors, finishedQuantity, packaging, issues, metadata);
    }
    public record Ingredient(String materialCode, String materialName, String role, BigDecimal weightKg,
                             int majorSequence, int stepSequence) {}
    public record Major(int sequence, String name, String yieldBasis, BigDecimal primaryInputKg,
                        BigDecimal primaryOutputKg, BigDecimal mainYieldPercent) {}
    public record FinishedQuantity(BigDecimal weightKg, Integer quantity, String unit, String sourceLabel) {}
    public record Issue(String code, String path, String message, Integer majorSequence, Integer stepSequence) {}
    public record Check(boolean ready, List<Issue> issues) {}
}
