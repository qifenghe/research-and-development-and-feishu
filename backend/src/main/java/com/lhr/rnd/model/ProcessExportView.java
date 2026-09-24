package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.util.List;

/** Read-only values bound to one saved plan/revision, never planned targets or editable form guesses. */
public record ProcessExportView(String productName, String sourceLabel, ProcessPlan snapshot,
        BigDecimal externalInputKg, BigDecimal mainYieldPercent, List<Ingredient> ingredients,
        List<Major> majors, FinishedQuantity finishedQuantity, List<PricingPackagingItem> packaging,
        List<Issue> issues, Metadata metadata) {
    public record Metadata(String specification, String compiledBy, String date, boolean inheritedActuals, String sourceTrialId) {
        public Metadata(String specification, String compiledBy, String date) { this(specification, compiledBy, date, false, null); }
    }
    public boolean inheritedActuals() { return metadata != null && metadata.inheritedActuals(); }
    public String actualPrefix() { return inheritedActuals() ? "方案记录" : "本次实验"; }
    public String actualProvenanceLabel() { return inheritedActuals() ? "含复制来源方案的继承实测，仅供对照；不表示本次新观测" : "本次试验已记录的实际值，不用计划值补齐"; }
    public ProcessExportView withActualsProvenance(boolean inherited, String sourceTrialId) {
        var meta = metadata == null ? new Metadata(null, null, null) : metadata;
        return new ProcessExportView(productName, sourceLabel + (inherited ? "（含继承实测）" : ""), snapshot, externalInputKg, mainYieldPercent, ingredients,
                majors, finishedQuantity, packaging, issues, new Metadata(meta.specification(), meta.compiledBy(), meta.date(), inherited, sourceTrialId));
    }
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
