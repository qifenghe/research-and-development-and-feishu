package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessSubmissionCheck;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessSubmissionValidatorTest {
    private final ProcessSubmissionValidator validator = new ProcessSubmissionValidator();

    @Test
    void requiresPositiveTotalExternalMaterialWeight() {
        var zeroWeight = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "0"), output("OUT-1", "0", true, false))), null);

        assertThat(validator.validate(zeroWeight).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("EXTERNAL_MATERIAL_WEIGHT_REQUIRED");
    }

    @Test
    void blocksStepOutputReferencesOutsideTheCurrentPlanAndReferencesToCurrentOrLaterSteps() {
        var foreign = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", "9", true, true)),
                step(2, material("PRIMARY", "STEP_OUTPUT", "FOREIGN-OUT", "牛肉", "9"), output("OUT-2", "8", true, false))), null);
        var currentOrLater = validPlan(List.of(step(1,
                material("PRIMARY", "STEP_OUTPUT", "OUT-2", "牛肉", "10"), output("OUT-1", "9", true, true)),
                step(2, material("PRIMARY", "STEP_OUTPUT", "OUT-1", "牛肉", "9"), output("OUT-2", "8", true, false))), null);

        assertThat(validator.validate(foreign).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("PRIMARY_FLOW_BROKEN");
        assertThat(validator.validate(currentOrLater).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("PRIMARY_FLOW_BROKEN");
    }

    @Test
    void blocksYieldMajorWithoutAFirstPrimaryInputOrLastPrimaryOutput() {
        var missingInput = validPlan(List.of(step(1,
                material("AUXILIARY", "EXTERNAL", null, "盐", "0.2"), output("OUT-1", "9", true, false))), null);
        var missingOutput = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), null)), null);

        assertThat(validator.validate(missingInput).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_PRIMARY_INPUT_REQUIRED", "EXTERNAL_PRIMARY_REQUIRED");
        assertThat(validator.validate(missingOutput).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_PRIMARY_OUTPUT_REQUIRED");
    }

    @Test
    void blocksForkedAndSkippedPrimaryChainsWithinAMajor() {
        var forked = validPlan(List.of(
                step(1, material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", "9", true, true)),
                step(2, material("PRIMARY", "STEP_OUTPUT", "OUT-1", "牛肉", "9"), output("OUT-2", "8", true, true)),
                step(3, material("PRIMARY", "STEP_OUTPUT", "OUT-1", "牛肉", "9"), output("OUT-3", "7", true, false))), null);
        var skipped = validPlan(List.of(
                step(1, material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", "9", true, true)),
                step(2, material("PRIMARY", "STEP_OUTPUT", "OUT-1", "牛肉", "9"), output("OUT-2", "8", true, true)),
                step(3, material("PRIMARY", "STEP_OUTPUT", "OUT-1", "牛肉", "9"), output("OUT-3", "7", true, false))), null);

        assertThat(validator.validate(forked).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("PRIMARY_FLOW_BROKEN");
        assertThat(validator.validate(skipped).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("PRIMARY_FLOW_BROKEN");
    }

    @Test
    void rejectsEmptyYieldExemptMajorAndZeroWeightPrimaryBoundary() {
        var valid = validPlan(validSteps(), null);
        var emptyNone = new ProcessPlan.MajorProcess("MAJOR-NONE", 2, "HOLD", "静置", null, "NONE", null,
                List.of(), List.of(), List.of(), null);
        var withEmptyNone = new ProcessPlan(valid.id(), valid.experimentFormId(), valid.versionNo(), valid.status(),
                List.of(valid.majorProcesses().get(0), emptyNone), null, false);
        var zeroPrimary = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "0"), output("OUT-ZERO", "1", true, false))), null);

        assertThat(validator.validate(withEmptyNone).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_STEP_REQUIRED", "MAJOR_YIELD_EXCLUSION_REASON_REQUIRED");
        assertThat(validator.validate(zeroPrimary).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED");

        var zeroLegacyMajor = new ProcessPlan.MajorProcess("MAJOR-LEGACY-ZERO", 2, "LEGACY", "旧工序", null,
                "PRIMARY_INPUT", "旧数据补录", List.of(),
                List.of(new ProcessPlan.ProcessInput("LEGACY-IN", 1, "PRIMARY", "BEEF", "牛肉", BigDecimal.ZERO, null)),
                List.of(new ProcessPlan.ProcessOutput("LEGACY-OUT", 1, "QUALIFIED", BigDecimal.ZERO, null)), null);
        var withZeroLegacy = new ProcessPlan(valid.id(), valid.experimentFormId(), valid.versionNo(), valid.status(),
                List.of(valid.majorProcesses().get(0), zeroLegacyMajor), null, false);
        assertThat(validator.validate(withZeroLegacy).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED", "MAJOR_PRIMARY_OUTPUT_WEIGHT_REQUIRED");
    }

    @Test
    void permitsDocumentedNoYieldMajorWithAnOperationalStep() {
        var valid = validPlan(validSteps(), null);
        var documented = new ProcessPlan.MajorProcess("MAJOR-NONE", 2, "PACKAGING", "包装", null, "NONE", "包装工序不改变主料重量",
                List.of(step(1, material("AUXILIARY", "EXTERNAL", null, "包装袋", "0.1"), null)), List.of(), List.of(), null);
        var plan = new ProcessPlan(valid.id(), valid.experimentFormId(), valid.versionNo(), valid.status(),
                List.of(valid.majorProcesses().get(0), documented), null, false);

        assertThat(validator.validate(plan).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .doesNotContain("MAJOR_STEP_REQUIRED", "MAJOR_YIELD_EXCLUSION_REASON_REQUIRED");
    }

    @Test
    void blocksYieldMajorWhenItsOnlyPrimaryOutputPrecedesItsPrimaryInput() {
        var plan = validPlan(List.of(
                step(1, material("AUXILIARY", "EXTERNAL", null, "盐", "0.2"), output("OUT-1", "9", true, true)),
                step(2, material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), null)), null);

        assertThat(validator.validate(plan).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MAJOR_PRIMARY_OUTPUT_REQUIRED", "MAJOR_PRIMARY_FLOW_INVALID");
    }

    @Test
    void blocksCriticalControlWithoutMeasurementResolvedDeviationAndConfirmation() {
        var unresolved = control("CRITICAL", false, null, List.of());
        var overLimit = control("CRITICAL", true, "研发", List.of(new ProcessPlan.ControlMeasurement("M-1", 1,
                new BigDecimal("72"), "2026-08-19T20:00:00", "FAIL", null, null, null)));

        assertThat(validator.validate(validPlan(validSteps(), unresolved)).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).contains("CRITICAL_CONTROL_UNRESOLVED");
        assertThat(validator.validate(validPlan(validSteps(), overLimit)).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).contains("CRITICAL_CONTROL_UNRESOLVED");
    }

    @Test
    void blocksAControlPointWhenAnyOutOfRangeMeasurementLacksItsOwnHandlingAndRetest() {
        var mixedMeasurements = control("CRITICAL", true, "研发", List.of(
                new ProcessPlan.ControlMeasurement("PASS-WITH-ACTION", 1, new BigDecimal("76"), "2026-08-19T20:00:00",
                        "PASS", "记录", "PASS", null),
                new ProcessPlan.ControlMeasurement("FAIL-WITHOUT-ACTION", 2, new BigDecimal("72"), "2026-08-19T20:02:00",
                        "FAIL", null, null, null)));

        assertThat(validator.validate(validPlan(validSteps(), mixedMeasurements)).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).contains("CRITICAL_CONTROL_UNRESOLVED");
    }

    @Test
    void permitsConfirmedCriticalControlWhenAllMeasurementsPassWithoutDeviationResolution() {
        var passing = control("CRITICAL", false, "研发", List.of(new ProcessPlan.ControlMeasurement("PASS", 1,
                new BigDecimal("76"), "2026-08-19T20:00:00", "PASS", null, null, null)));

        assertThat(validator.validate(validPlan(validSteps(), passing)).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).doesNotContain("CRITICAL_CONTROL_UNRESOLVED");
    }

    @Test
    void blocksOutOfRangeMeasurementWhenTheControlPointIsNotResolved() {
        var unresolvedDeviation = control("CRITICAL", false, "研发", List.of(new ProcessPlan.ControlMeasurement("FAIL", 1,
                new BigDecimal("72"), "2026-08-19T20:00:00", "FAIL", "继续加热", "PASS", null)));

        assertThat(validator.validate(validPlan(validSteps(), unresolvedDeviation)).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).contains("CRITICAL_CONTROL_UNRESOLVED");
    }

    @Test
    void permitsAnExplainedMaterialBalanceWarning() {
        var plan = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", "8", true, false))), null);
        var major = plan.majorProcesses().get(0);
        plan = new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(
                new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(),
                        major.description(), major.yieldBasis(), "烹煮损耗已说明", major.steps(), major.inputs(), major.outputs(), null)),
                plan.batchYieldPercent(), plan.legacy());

        var result = validator.validate(plan);

        assertThat(result.ready()).isTrue();
        assertThat(result.warnings()).extracting(ProcessSubmissionCheck.Issue::code).contains("MATERIAL_BALANCE_EXCEEDED");
    }

    @Test
    void appliesThePlanBalanceToleranceAtItsBoundary() {
        var atTolerance = planWithBalance("9.9900", "已说明", new BigDecimal("0.0100"));
        var aboveToleranceExplained = planWithBalance("9.9899", "已说明", new BigDecimal("0.0100"));
        var aboveToleranceUnexplained = planWithBalance("9.9899", null, new BigDecimal("0.0100"));

        assertThat(validator.validate(atTolerance).warnings()).extracting(ProcessSubmissionCheck.Issue::code)
                .doesNotContain("MATERIAL_BALANCE_EXCEEDED");
        assertThat(validator.validate(aboveToleranceExplained).ready()).isTrue();
        assertThat(validator.validate(aboveToleranceExplained).warnings()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MATERIAL_BALANCE_EXCEEDED");
        assertThat(validator.validate(aboveToleranceUnexplained).errors()).extracting(ProcessSubmissionCheck.Issue::code)
                .contains("MATERIAL_BALANCE_UNEXPLAINED");
    }

    @Test
    void rejectsNegativePlanBalanceTolerance() {
        assertThat(validator.validate(planWithBalance("10", "无差异", new BigDecimal("-0.0100"))).errors())
                .extracting(ProcessSubmissionCheck.Issue::code).contains("BALANCE_TOLERANCE_INVALID");
    }

    private ProcessPlan validPlan(List<ProcessPlan.MinorStep> steps, ProcessPlan.ControlPoint control) {
        var first = steps.get(0);
        var controlled = control == null ? first : new ProcessPlan.MinorStep(first.id(), first.sequence(), first.stepCode(), first.stepName(),
                first.stepType(), first.parameter1Name(), first.parameter1Value(), first.parameter1Unit(), first.parameter2Name(),
                first.parameter2Value(), first.parameter2Unit(), first.equipment(), first.instruction(), first.materials(), first.outputs(), List.of(control));
        var copied = new java.util.ArrayList<>(steps);
        copied.set(0, controlled);
        var major = new ProcessPlan.MajorProcess("MAJOR-1", 1, "HEAT", "热加工", null, "PRIMARY_INPUT", null,
                copied, List.of(), List.of(), null);
        return new ProcessPlan("PLAN-1", "FORM-1", 1, "DRAFT", List.of(major), null, false);
    }

    private ProcessPlan planWithBalance(String outputWeight, String remark, BigDecimal tolerance) {
        var plan = validPlan(List.of(step(1,
                material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", outputWeight, true, false))), null);
        var major = plan.majorProcesses().get(0);
        var explained = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(),
                major.description(), major.yieldBasis(), remark, major.steps(), major.inputs(), major.outputs(), null);
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(explained),
                plan.batchYieldPercent(), tolerance, plan.legacy());
    }

    private List<ProcessPlan.MinorStep> validSteps() {
        return List.of(step(1, material("PRIMARY", "EXTERNAL", null, "牛肉", "10"), output("OUT-1", "8", true, false)));
    }

    private ProcessPlan.MinorStep step(int sequence, ProcessPlan.StepMaterial material, ProcessPlan.StepOutput output) {
        return new ProcessPlan.MinorStep("STEP-" + sequence, sequence, null, "步骤" + sequence, "NORMAL", null, null,
                null, null, null, null, null, null, List.of(material), output == null ? List.of() : List.of(output), List.of());
    }

    private ProcessPlan.StepMaterial material(String role, String sourceType, String sourceOutputId, String name, String weight) {
        return new ProcessPlan.StepMaterial("MAT-" + name + '-' + weight, 1, role, null, name, "SOLID", new BigDecimal(weight),
                "PRIMARY".equals(role) ? "BEEF-1" : null, null, sourceType, sourceOutputId);
    }

    private ProcessPlan.StepOutput output(String id, String weight, boolean primary, boolean continueFlow) {
        return new ProcessPlan.StepOutput(id, 1, "INTERMEDIATE", "产出", "SOLID", new BigDecimal(weight), primary, continueFlow, null);
    }

    private ProcessPlan.ControlPoint control(String importance, boolean resolved, String confirmedBy,
                                              List<ProcessPlan.ControlMeasurement> measurements) {
        return new ProcessPlan.ControlPoint("CP-1", 1, "FOOD_SAFETY", importance, "中心温度", new BigDecimal("75"),
                new BigDecimal("75"), null, "℃", null, null, "继续加热", resolved, confirmedBy, measurements);
    }
}
