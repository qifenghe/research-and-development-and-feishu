package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record TrialScheme(
        String id,
        String experimentFormId,
        int versionNo,
        String name,
        String sourceTrialId,
        boolean archived,
        String purpose,
        String variables,
        Conclusion conclusion,
        String recommendationReason,
        BigDecimal qualityScore,
        String qualityNotes,
        Difficulty difficulty,
        ProcessPlan plan,
        PlannedData plannedData,
        boolean inheritedActuals,
        Map<String, String> majorOrigins,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
    public enum Conclusion { PENDING, ADJUST, REJECT, RECOMMEND }
    public enum Difficulty { EASY, MEDIUM, HARD }

    public record StepParameters(String parameter1Value, String parameter2Value) {
    }

    public record PlannedData(
            Map<String, BigDecimal> materialWeightsKg,
            Map<String, StepParameters> stepParameters,
            Map<String, BigDecimal> majorYieldTargets,
            BigDecimal batchYieldTarget,
            String yieldBasisNote
    ) {
        public PlannedData {
            materialWeightsKg = immutable(materialWeightsKg);
            stepParameters = immutable(stepParameters);
            majorYieldTargets = immutable(majorYieldTargets);
        }

        public static PlannedData empty() {
            return new PlannedData(Map.of(), Map.of(), Map.of(), null, null);
        }

        private static <K, V> Map<K, V> immutable(Map<K, V> values) {
            return values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
        }
    }
}
