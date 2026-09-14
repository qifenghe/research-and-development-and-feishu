package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProcessFlowRegressionTest {
    private final ProcessPlanCalculationService calculation = new ProcessPlanCalculationService();
    private final ProcessSubmissionValidator validator = new ProcessSubmissionValidator();

    @Test void countsTransferredMaterialAtMajorBoundary() {
        var value = calculation.calculate(major(2, "o1", "90", "72"));
        assertThat(value.totalInputWeightKg()).isEqualByComparingTo("90");
        assertThat(value.balanceDifferenceKg()).isEqualByComparingTo("18");
    }
    @Test void acceptsConnectedRouteAndCalculatesFinalYield() {
        var plan = plan(major(1, null, "100", "90"), major(2, "o1", "90", "72"));
        assertThat(validator.validate(plan).ready()).isTrue();
        assertThat(calculation.calculateBatch(plan)).isEqualByComparingTo("72");
    }
    @Test void rejectsTransferWeightMismatchAndWithholdsBatchYield() {
        var plan = plan(major(1, null, "100", "90"), major(2, "o1", "100", "72"));
        assertThat(validator.validate(plan).errors()).extracting("code").contains("FLOW_WEIGHT_MISMATCH");
        assertThat(calculation.calculateBatch(plan)).isNull();
    }
    @Test void doesNotMultiplyIndependentPrimaryRoutes() {
        var plan = plan(major(1, null, "100", "90"), major(2, null, "100", "80"));
        assertThat(validator.validate(plan).errors()).extracting("code").contains("BATCH_PRIMARY_CHAIN_REQUIRED");
        assertThat(calculation.calculateBatch(plan)).isNull();
    }
    @Test void missingTerminalWeightCannotFallBackToEarlierOutput() {
        var first = major(1, null, "100", "90");
        var second = major(2, "o1", "90", null);
        var tail = second.steps().get(0);
        var orderedTail = new ProcessPlan.MinorStep(tail.id(), 2, tail.stepCode(), tail.stepName(), tail.stepType(), null, null, null, null, null, null, null, null, tail.materials(), tail.outputs(), List.of());
        var combined = new ProcessPlan.MajorProcess("g", 1, "G", "工序", null, "PRIMARY_INPUT", "损耗", List.of(first.steps().get(0), orderedTail), List.of(), List.of(), null);
        assertThat(validator.validate(plan(combined)).ready()).isFalse();
        assertThat(calculation.calculateBatch(plan(combined))).isNull();
        assertThat(calculation.calculate(combined).mainYieldPercent()).isNull();
    }
    @Test void countedLegacyMajorCannotBypassPrimaryChain() {
        var legacy = new ProcessPlan.MajorProcess("legacy", 2, "OLD", "旧工序", null, "PRIMARY_INPUT", null, List.of(), List.of(), List.of(), null);
        assertThat(validator.flowIssues(plan(major(1, null, "100", "90"), legacy)))
                .extracting("code").contains("MAJOR_PRIMARY_CHAIN_REQUIRED");
    }
    private ProcessPlan plan(ProcessPlan.MajorProcess... majors) {
        return new ProcessPlan("p", "f", 1, "DRAFT", List.of(majors), null, false);
    }
    private ProcessPlan.MajorProcess major(int seq, String source, String input, String output) {
        var material = new ProcessPlan.StepMaterial("m"+seq, 1, "PRIMARY", "MEAT", "牛肉", "SOLID", new BigDecimal(input), null, null, source == null ? "EXTERNAL" : "STEP_OUTPUT", source);
        var result = new ProcessPlan.StepOutput("o"+seq, 1, "QUALIFIED", "牛肉", "SOLID", output == null ? null : new BigDecimal(output), true, true, null);
        var step = new ProcessPlan.MinorStep("s"+seq, 1, "STEP", "操作", "NORMAL", null, null, null, null, null, null, null, null, List.of(material), List.of(result), List.of());
        return new ProcessPlan.MajorProcess("g"+seq, seq, "G"+seq, "工序"+seq, null, "PRIMARY_INPUT", "正常工艺损耗", List.of(step), List.of(), List.of(), null);
    }
}
