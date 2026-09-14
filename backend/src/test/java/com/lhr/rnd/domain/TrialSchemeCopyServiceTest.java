package com.lhr.rnd.domain;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.TrialScheme;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrialSchemeCopyServiceTest {
    private final TrialSchemeCopyService service = new TrialSchemeCopyService();

    @Test
    void defaultCopyMovesSupportedActualsIntoMissingPlansAndClearsActuals() {
        var source = graph();
        var copy = service.copy(source, TrialScheme.PlannedData.empty(), false, Map.of("MAJOR-SOURCE", "MAJOR-ORIGIN"));
        var major = copy.plan().majorProcesses().get(0);
        var step = major.steps().get(0);

        assertThat(step.materials().get(0).weightKg()).isNull();
        assertThat(step.parameter1Value()).isNull();
        assertThat(step.parameter2Value()).isNull();
        assertThat(step.controlPoints().get(0).measurements()).isEmpty();
        assertThat(copy.plannedData().materialWeightsKg()).containsEntry(step.materials().get(0).id(), new BigDecimal("10.2"));
        assertThat(copy.plannedData().stepParameters().get(step.id()).parameter1Value()).isEqualTo("30");
        assertThat(copy.plannedData().majorYieldTargets()).containsEntry(major.id(), new BigDecimal("90"));
        assertThat(copy.plannedData().batchYieldTarget()).isEqualByComparingTo("88");
        assertThat(copy.majorOrigins()).containsEntry(major.id(), "MAJOR-ORIGIN");
        assertThat(copy.inheritedMeasurementIds()).isEmpty();
    }

    @Test
    void explicitPlansWinWhenDefaultCopyMovesActuals() {
        var plans = new TrialScheme.PlannedData(
                Map.of("MAT-SOURCE", new BigDecimal("9.5")),
                Map.of("STEP-SOURCE", new TrialScheme.StepParameters("25", "90")),
                Map.of("MAJOR-SOURCE", new BigDecimal("85")), new BigDecimal("80"), "原口径");
        var copy = service.copy(graph(), plans, false, Map.of());
        var major = copy.plan().majorProcesses().get(0);
        var step = major.steps().get(0);

        assertThat(copy.plannedData().materialWeightsKg()).containsEntry(step.materials().get(0).id(), new BigDecimal("9.5"));
        assertThat(copy.plannedData().stepParameters().get(step.id())).isEqualTo(new TrialScheme.StepParameters("25", "90"));
        assertThat(copy.plannedData().majorYieldTargets()).containsEntry(major.id(), new BigDecimal("85"));
        assertThat(copy.plannedData().batchYieldTarget()).isEqualByComparingTo("80");
    }

    @Test
    void copyWithActualsMarksEveryMeasurementAsInheritedButClearsConfirmationAndRelease() {
        var copy = service.copy(graph(), TrialScheme.PlannedData.empty(), true, Map.of());
        var point = copy.plan().majorProcesses().get(0).steps().get(0).controlPoints().get(0);

        assertThat(point.measurements()).hasSize(1);
        assertThat(copy.inheritedMeasurementIds()).containsExactly(point.measurements().get(0).id());
        assertThat(point.confirmedBy()).isNull();
        assertThat(point.confirmedAt()).isNull();
        assertThat(point.resolved()).isFalse();
        assertThat(point.measurements().get(0).retestResult()).isNull();
        assertThat(point.measurements().get(0).deviationAction()).isEqualTo("已处理");
    }

    @Test
    void remapsAllIdsAndReferencesAndRejectsDanglingReferences() {
        var copy = service.copy(graph(), new TrialScheme.PlannedData(Map.of("MAT-SOURCE", BigDecimal.TEN), Map.of(), Map.of(), null, null), true, Map.of());
        var source = graph();
        var copiedStep = copy.plan().majorProcesses().get(0).steps().get(0);

        assertThat(copiedStep.id()).isNotEqualTo(source.majorProcesses().get(0).steps().get(0).id());
        assertThat(copiedStep.materials().get(0).sourceStepOutputId()).isEqualTo(copiedStep.outputs().get(0).id());
        assertThat(copy.plannedData().materialWeightsKg()).containsKey(copiedStep.materials().get(0).id());

        var bad = withDanglingOutput(graph());
        assertThatThrownBy(() -> service.copy(bad, TrialScheme.PlannedData.empty(), false, Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_GRAPH_REFERENCE_INVALID");
    }

    private ProcessPlan graph() {
        var measurement = new ProcessPlan.ControlMeasurement("MEASURE-SOURCE", 1, new BigDecimal("80"), "2026-09-15T10:00:00", "PASS", "已处理", "PASS", null);
        var point = new ProcessPlan.ControlPoint("POINT-SOURCE", 1, "TEMPERATURE", "CRITICAL", "中心温度", new BigDecimal("80"),
                new BigDecimal("75"), new BigDecimal("90"), "℃", "探针", "温度计", "每批", "复测", true, "来源确认人", "2026-09-15T11:00:00", null, List.of(measurement));
        var output = new ProcessPlan.StepOutput("OUTPUT-SOURCE", 1, "MAIN", "熟制牛腩", "COOKED", new BigDecimal("9"), true, true, null);
        var material = new ProcessPlan.StepMaterial("MAT-SOURCE", 1, "MAIN", null, "承接物", "RAW", new BigDecimal("10.2"),
                null, null, "STEP_OUTPUT", "OUTPUT-SOURCE");
        var step = new ProcessPlan.MinorStep("STEP-SOURCE", 1, "S1", "熟制", "NORMAL", "时间", "30", "min", "温度", "95", "℃",
                "蒸箱", "熟制", List.of(material), List.of(output), List.of(point));
        var input = new ProcessPlan.ProcessInput("INPUT-SOURCE", 1, "MAIN", null, "承接物", new BigDecimal("10.2"), "MAT-SOURCE");
        var processOutput = new ProcessPlan.ProcessOutput("POUT-SOURCE", 1, "QUALIFIED", new BigDecimal("9"), null);
        var yield = new ProcessPlan.ProcessYield(new BigDecimal("10"), new BigDecimal("10.2"), new BigDecimal("9"), BigDecimal.ZERO,
                new BigDecimal("9"), new BigDecimal("90"), new BigDecimal("90"), new BigDecimal("1.2"));
        var major = new ProcessPlan.MajorProcess("MAJOR-SOURCE", 1, "P1", "熟制", null, "PRIMARY_INPUT", null,
                List.of(step), List.of(input), List.of(processOutput), yield);
        return new ProcessPlan("PLAN-SOURCE", "FORM", 4, "DRAFT", List.of(major), new BigDecimal("88"), new BigDecimal("0.01"), false);
    }

    private ProcessPlan withDanglingOutput(ProcessPlan plan) {
        var major = plan.majorProcesses().get(0);
        var step = major.steps().get(0);
        var old = step.materials().get(0);
        var badMaterial = new ProcessPlan.StepMaterial(old.id(), old.sequence(), old.materialRole(), old.materialCode(), old.materialName(), old.materialState(),
                old.weightKg(), old.formulaMaterialId(), old.remark(), "STEP_OUTPUT", "OUTPUT-FOREIGN");
        var badStep = new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(), step.stepType(), step.parameter1Name(),
                step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(), step.parameter2Value(), step.parameter2Unit(), step.equipment(),
                step.instruction(), List.of(badMaterial), step.outputs(), step.controlPoints());
        var badMajor = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(), major.description(),
                major.yieldBasis(), major.remark(), List.of(badStep), major.inputs(), major.outputs(), major.yield());
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(badMajor), plan.batchYieldPercent(),
                plan.balanceToleranceKg(), false);
    }
}
