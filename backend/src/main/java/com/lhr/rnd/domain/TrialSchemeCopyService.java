package com.lhr.rnd.domain;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.TrialScheme;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TrialSchemeCopyService {
    public CopyResult copy(ProcessPlan source, TrialScheme.PlannedData sourcePlannedData, boolean includeActuals,
                           Map<String, String> sourceMajorOrigins) {
        var remapper = Remapper.allNew(source);
        var planned = remapPlanned(sourcePlannedData, remapper, source);
        if (!includeActuals) planned = moveActualsIntoMissingPlans(source, planned, remapper);
        var mapped = remapPlan(source, remapper, includeActuals);
        var origins = remapMajorOrigins(source, mapped, sourceMajorOrigins);
        var inheritedMeasurements = includeActuals ? mappedMeasurementIds(source, remapper) : Set.<String>of();
        return new CopyResult(mapped, planned, origins, inheritedMeasurements);
    }

    public CopyResult normalizeNew(ProcessPlan source, TrialScheme.PlannedData plannedData) {
        var remapper = Remapper.allNew(source);
        var mapped = remapPlan(source, remapper, true);
        return new CopyResult(mapped, remapPlanned(plannedData, remapper, source),
                remapMajorOrigins(source, mapped, Map.of()), Set.of());
    }

    public CopyResult normalizeForSave(ProcessPlan request, TrialScheme.PlannedData plannedData, ProcessPlan persisted,
                                       Map<String, String> persistedOrigins, Set<String> inheritedMeasurementIds) {
        var remapper = Remapper.preservePersisted(request, persisted);
        var mapped = remapPlan(request, remapper, true);
        return new CopyResult(mapped, remapPlanned(plannedData, remapper, request),
                remapMajorOriginsForSave(request, mapped, persistedOrigins), Set.copyOf(inheritedMeasurementIds));
    }

    private ProcessPlan remapPlan(ProcessPlan source, Remapper ids, boolean includeActuals) {
        var majors = values(source == null ? null : source.majorProcesses()).stream().map(major -> {
            var steps = values(major.steps()).stream().map(step -> {
                var materials = values(step.materials()).stream().map(material -> new ProcessPlan.StepMaterial(
                        ids.id("TMAT", material.id()), material.sequence(), material.materialRole(), material.materialCode(), material.materialName(),
                        material.materialState(), includeActuals ? material.weightKg() : null, material.formulaMaterialId(), material.remark(),
                        material.sourceType(), ids.reference(material.sourceStepOutputId()))).toList();
                var outputs = values(step.outputs()).stream().map(output -> new ProcessPlan.StepOutput(
                        ids.id("TOUT", output.id()), output.sequence(), output.outputType(), output.outputName(), output.materialState(),
                        includeActuals ? output.weightKg() : null, output.primaryOutput(), output.continueFlow(), output.remark())).toList();
                var points = values(step.controlPoints()).stream().map(point -> new ProcessPlan.ControlPoint(
                        ids.id("TCP", point.id()), point.sequence(), point.controlType(), point.importance(), point.itemName(), point.targetValue(),
                        point.lowerLimit(), point.upperLimit(), point.unit(), point.method(), point.measurementTool(), point.frequency(),
                        point.deviationAction(), false, null, null, point.basisOrRemark(),
                        includeActuals ? values(point.measurements()).stream().map(measurement -> new ProcessPlan.ControlMeasurement(
                                ids.id("TCM", measurement.id()), measurement.sequence(), measurement.measuredValue(), measurement.measuredAt(),
                                measurement.result(), measurement.deviationAction(), null, measurement.remark())).toList() : List.of())).toList();
                return new ProcessPlan.MinorStep(ids.id("TSTEP", step.id()), step.sequence(), step.stepCode(), step.stepName(), step.stepType(),
                        step.parameter1Name(), includeActuals ? step.parameter1Value() : null, step.parameter1Unit(), step.parameter2Name(),
                        includeActuals ? step.parameter2Value() : null, step.parameter2Unit(), step.equipment(), step.instruction(), materials, outputs, points);
            }).toList();
            var inputs = values(major.inputs()).stream().map(input -> new ProcessPlan.ProcessInput(
                    ids.id("TIN", input.id()), input.sequence(), input.inputRole(), input.materialCode(), input.materialName(),
                    includeActuals ? input.weightKg() : null, ids.reference(input.sourceStepMaterialId()))).toList();
            var outputs = values(major.outputs()).stream().map(output -> new ProcessPlan.ProcessOutput(
                    ids.id("TPOUT", output.id()), output.sequence(), output.outputType(), includeActuals ? output.weightKg() : null, output.remark())).toList();
            return new ProcessPlan.MajorProcess(ids.id("TMAJ", major.id()), major.sequence(), major.processCode(), major.processName(),
                    major.description(), major.yieldBasis(), major.remark(), steps, inputs, outputs, includeActuals ? major.yield() : null);
        }).toList();
        return new ProcessPlan(ids.planId(), source == null ? null : source.experimentFormId(), 1, "DRAFT", majors,
                includeActuals && source != null ? source.batchYieldPercent() : null,
                source == null ? ProcessPlan.DEFAULT_BALANCE_TOLERANCE_KG : source.balanceToleranceKg(), false, null, null);
    }

    private TrialScheme.PlannedData moveActualsIntoMissingPlans(ProcessPlan source, TrialScheme.PlannedData current, Remapper ids) {
        var weights = new LinkedHashMap<>(current.materialWeightsKg());
        var parameters = new LinkedHashMap<>(current.stepParameters());
        var yields = new LinkedHashMap<>(current.majorYieldTargets());
        for (var major : values(source.majorProcesses())) {
            var mappedMajorId = ids.reference(major.id());
            if (major.yield() != null && major.yield().mainYieldPercent() != null) {
                yields.putIfAbsent(mappedMajorId, major.yield().mainYieldPercent());
            }
            for (var step : values(major.steps())) {
                var mappedStepId = ids.reference(step.id());
                if (step.parameter1Value() != null || step.parameter2Value() != null) {
                    parameters.putIfAbsent(mappedStepId, new TrialScheme.StepParameters(step.parameter1Value(), step.parameter2Value()));
                }
                for (var material : values(step.materials())) {
                    if (material.weightKg() != null) weights.putIfAbsent(ids.reference(material.id()), material.weightKg());
                }
            }
        }
        var batchTarget = current.batchYieldTarget() != null ? current.batchYieldTarget() : source.batchYieldPercent();
        return new TrialScheme.PlannedData(weights, parameters, yields, batchTarget, current.yieldBasisNote());
    }

    private TrialScheme.PlannedData remapPlanned(TrialScheme.PlannedData plannedData, Remapper ids, ProcessPlan plan) {
        var source = plannedData == null ? TrialScheme.PlannedData.empty() : plannedData;
        var materialIds = collectMaterialIds(plan);
        var stepIds = collectStepIds(plan);
        var majorIds = collectMajorIds(plan);
        var weights = remapMap(source.materialWeightsKg(), materialIds, ids);
        var parameters = remapMap(source.stepParameters(), stepIds, ids);
        var yields = remapMap(source.majorYieldTargets(), majorIds, ids);
        return new TrialScheme.PlannedData(weights, parameters, yields, source.batchYieldTarget(), source.yieldBasisNote());
    }

    private <T> Map<String, T> remapMap(Map<String, T> source, Set<String> allowed, Remapper ids) {
        var result = new LinkedHashMap<String, T>();
        for (var entry : values(source).entrySet()) {
            if (blank(entry.getKey()) || !allowed.contains(entry.getKey()) || entry.getValue() == null) throw invalidReference();
            result.put(ids.reference(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private Map<String, String> remapMajorOrigins(ProcessPlan source, ProcessPlan mapped, Map<String, String> existing) {
        var result = new LinkedHashMap<String, String>();
        var sourceMajors = values(source.majorProcesses());
        var mappedMajors = values(mapped.majorProcesses());
        for (int index = 0; index < sourceMajors.size(); index++) {
            var major = sourceMajors.get(index);
            var mappedId = mappedMajors.get(index).id();
            var origin = major.id() == null ? null : values(existing).get(major.id());
            result.put(mappedId, origin == null ? (major.id() == null ? mappedId : major.id()) : origin);
        }
        return result;
    }

    private Map<String, String> remapMajorOriginsForSave(ProcessPlan request, ProcessPlan mapped, Map<String, String> existing) {
        var result = new LinkedHashMap<String, String>();
        var sourceMajors = values(request.majorProcesses());
        var mappedMajors = values(mapped.majorProcesses());
        for (int index = 0; index < sourceMajors.size(); index++) {
            var major = sourceMajors.get(index);
            var mappedId = mappedMajors.get(index).id();
            var origin = major.id() == null ? null : values(existing).get(major.id());
            result.put(mappedId, origin == null ? mappedId : origin);
        }
        return result;
    }

    private Set<String> mappedMeasurementIds(ProcessPlan source, Remapper ids) {
        var result = new LinkedHashSet<String>();
        for (var major : values(source.majorProcesses())) for (var step : values(major.steps()))
            for (var point : values(step.controlPoints())) for (var measurement : values(point.measurements()))
                result.add(ids.reference(measurement.id()));
        return result;
    }

    private Set<String> collectMajorIds(ProcessPlan plan) {
        var result = new HashSet<String>();
        for (var major : values(plan == null ? null : plan.majorProcesses())) if (!blank(major.id())) result.add(major.id());
        return result;
    }

    private Set<String> collectStepIds(ProcessPlan plan) {
        var result = new HashSet<String>();
        for (var major : values(plan == null ? null : plan.majorProcesses())) for (var step : values(major.steps())) if (!blank(step.id())) result.add(step.id());
        return result;
    }

    private Set<String> collectMaterialIds(ProcessPlan plan) {
        var result = new HashSet<String>();
        for (var major : values(plan == null ? null : plan.majorProcesses())) for (var step : values(major.steps()))
            for (var material : values(step.materials())) if (!blank(material.id())) result.add(material.id());
        return result;
    }

    private static <T> List<T> values(List<T> values) { return values == null ? List.of() : values; }
    private static <K, V> Map<K, V> values(Map<K, V> values) { return values == null ? Map.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static BusinessException invalidReference() {
        return new BusinessException("TRIAL_GRAPH_REFERENCE_INVALID", "试验方案包含无效或跨方案引用");
    }

    public record CopyResult(ProcessPlan plan, TrialScheme.PlannedData plannedData, Map<String, String> majorOrigins,
                             Set<String> inheritedMeasurementIds) {
    }

    private static final class Remapper {
        private final Map<String, String> ids = new HashMap<>();
        private final Set<String> outputReferences = new HashSet<>();
        private final Set<String> materialReferences = new HashSet<>();
        private final Set<String> seen = new HashSet<>();
        private final Set<String> persistedIds;
        private final boolean replaceAll;
        private String planId;

        private Remapper(ProcessPlan request, Set<String> persistedIds, boolean replaceAll, String persistedPlanId) {
            this.persistedIds = persistedIds;
            this.replaceAll = replaceAll;
            scan(request);
            planId = persistedPlanId != null ? persistedPlanId : newId("TPLAN");
        }

        static Remapper allNew(ProcessPlan source) { return new Remapper(source, Set.of(), true, null); }
        static Remapper preservePersisted(ProcessPlan request, ProcessPlan persisted) {
            return new Remapper(request, allIds(persisted), false, persisted == null ? null : persisted.id());
        }

        String planId() { return planId; }

        String id(String prefix, String sourceId) {
            if (blank(sourceId)) return newId(prefix);
            return ids.computeIfAbsent(sourceId, key -> !replaceAll && persistedIds.contains(key) ? key : newId(prefix));
        }

        String reference(String sourceId) {
            if (blank(sourceId)) return null;
            if (!seen.contains(sourceId)) throw invalidReference();
            var mapped = ids.get(sourceId);
            if (mapped == null) throw invalidReference();
            return mapped;
        }

        private void scan(ProcessPlan plan) {
            if (plan == null) throw new BusinessException("TRIAL_PLAN_REQUIRED", "试验方案工艺数据不能为空");
            for (var major : values(plan.majorProcesses())) {
                add(major.id());
                for (var step : values(major.steps())) {
                    add(step.id());
                    for (var material : values(step.materials())) { add(material.id()); if (!blank(material.id())) materialReferences.add(material.id()); }
                    for (var output : values(step.outputs())) { add(output.id()); if (!blank(output.id())) outputReferences.add(output.id()); }
                    for (var point : values(step.controlPoints())) {
                        add(point.id());
                        for (var measurement : values(point.measurements())) add(measurement.id());
                    }
                }
                for (var input : values(major.inputs())) add(input.id());
                for (var output : values(major.outputs())) add(output.id());
            }
            // Allocate mappings before validating references so forward references are valid.
            for (var major : values(plan.majorProcesses())) {
                id("TMAJ", major.id());
                for (var step : values(major.steps())) {
                    id("TSTEP", step.id());
                    for (var material : values(step.materials())) id("TMAT", material.id());
                    for (var output : values(step.outputs())) id("TOUT", output.id());
                    for (var point : values(step.controlPoints())) {
                        id("TCP", point.id());
                        for (var measurement : values(point.measurements())) id("TCM", measurement.id());
                    }
                }
                for (var input : values(major.inputs())) id("TIN", input.id());
                for (var output : values(major.outputs())) id("TPOUT", output.id());
            }
            for (var major : values(plan.majorProcesses())) {
                for (var step : values(major.steps())) for (var material : values(step.materials())) {
                    if (!blank(material.sourceStepOutputId()) && !outputReferences.contains(material.sourceStepOutputId())) throw invalidReference();
                }
                for (var input : values(major.inputs())) {
                    if (!blank(input.sourceStepMaterialId()) && !materialReferences.contains(input.sourceStepMaterialId())) throw invalidReference();
                }
            }
        }

        private void add(String sourceId) {
            if (blank(sourceId)) return;
            if (!seen.add(sourceId)) throw invalidReference();
        }

        private static Set<String> allIds(ProcessPlan plan) {
            var result = new HashSet<String>();
            if (plan == null) return result;
            if (!blank(plan.id())) result.add(plan.id());
            for (var major : values(plan.majorProcesses())) {
                if (!blank(major.id())) result.add(major.id());
                for (var step : values(major.steps())) {
                    if (!blank(step.id())) result.add(step.id());
                    for (var material : values(step.materials())) if (!blank(material.id())) result.add(material.id());
                    for (var output : values(step.outputs())) if (!blank(output.id())) result.add(output.id());
                    for (var point : values(step.controlPoints())) {
                        if (!blank(point.id())) result.add(point.id());
                        for (var measurement : values(point.measurements())) if (!blank(measurement.id())) result.add(measurement.id());
                    }
                }
                for (var input : values(major.inputs())) if (!blank(input.id())) result.add(input.id());
                for (var output : values(major.outputs())) if (!blank(output.id())) result.add(output.id());
            }
            return result;
        }

        private static String newId(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    }
}
