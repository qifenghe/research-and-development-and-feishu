package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProcessRecipeService {
    private static final int WEIGHT_SCALE = 4;
    private static final int RATE_SCALE = 6;

    public List<RecipeLine> aggregate(ProcessPlan plan) {
        var groups = new LinkedHashMap<MaterialKey, Aggregate>();
        for (var major : values(plan == null ? null : plan.majorProcesses())) {
            for (var step : values(major.steps())) {
                for (var material : values(step.materials())) {
                    if (!"EXTERNAL".equals(material.sourceType())) continue;
                    var name = normalizedName(material.materialName());
                    var key = MaterialKey.of(material.formulaMaterialId(), material.materialCode(), name);
                    var aggregate = groups.computeIfAbsent(key, ignored -> new Aggregate(material.formulaMaterialId(),
                            material.materialCode(), name));
                    aggregate.weight = aggregate.weight.add(weight(material.weightKg()));
                    aggregate.sources.add(new RecipeSource(major.sequence(), step.sequence(), material.sequence(),
                            material.materialRole(), material.materialCode(), name, material.weightKg()));
                }
            }
        }
        var totalWeight = groups.values().stream().map(aggregate -> aggregate.weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            var aggregate = entry.getValue();
            var ratio = totalWeight.signum() == 0 ? null : aggregate.weight.multiply(BigDecimal.valueOf(100))
                    .divide(totalWeight, RATE_SCALE, RoundingMode.HALF_UP);
            var sources = aggregate.sources.stream().sorted(Comparator.comparingInt(RecipeSource::majorSequence)
                    .thenComparingInt(RecipeSource::stepSequence).thenComparingInt(RecipeSource::materialSequence)).toList();
            return new RecipeLine(aggregate.formulaMaterialId, aggregate.materialCode, aggregate.materialName,
                    aggregate.weight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP), ratio, sources);
        }).toList();
    }

    public record RecipeLine(
            String formulaMaterialId,
            String materialCode,
            String materialName,
            BigDecimal weightKg,
            BigDecimal ratioPercent,
            List<RecipeSource> sources
    ) {
        public RecipeLine {
            sources = List.copyOf(sources);
        }

        public String canonicalMaterialRole() {
            if (sources.stream().anyMatch(source -> "PRIMARY".equals(source.materialRole()))) return "PRIMARY";
            if (sources.stream().anyMatch(source -> "PROCESS_WATER".equals(source.materialRole()))) return "PROCESS_WATER";
            return "AUXILIARY";
        }
    }

    public record RecipeSource(
            int majorSequence,
            int stepSequence,
            int materialSequence,
            String materialRole,
            String materialCode,
            String materialName,
            BigDecimal weightKg
    ) {
    }

    private static final class Aggregate {
        private final String formulaMaterialId;
        private final String materialCode;
        private final String materialName;
        private BigDecimal weight = BigDecimal.ZERO;
        private final List<RecipeSource> sources = new ArrayList<>();

        private Aggregate(String formulaMaterialId, String materialCode, String materialName) {
            this.formulaMaterialId = formulaMaterialId;
            this.materialCode = materialCode;
            this.materialName = materialName;
        }
    }

    private record MaterialKey(int type, String value) implements Comparable<MaterialKey> {
        private static MaterialKey of(String formulaMaterialId, String materialCode, String materialName) {
            if (!blank(formulaMaterialId)) return new MaterialKey(0, formulaMaterialId.trim());
            if (!blank(materialCode)) return new MaterialKey(1, materialCode.trim());
            return new MaterialKey(2, materialName);
        }

        @Override
        public int compareTo(MaterialKey other) {
            var byType = Integer.compare(type, other.type);
            return byType != 0 ? byType : value.compareTo(other.value);
        }
    }

    private static String normalizedName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BigDecimal weight(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }
}
