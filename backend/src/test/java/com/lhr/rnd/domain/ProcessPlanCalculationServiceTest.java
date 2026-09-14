package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessPlanCalculationServiceTest {
    private final ProcessPlanCalculationService service = new ProcessPlanCalculationService();

    @Test
    void calculatesMinorStepYieldFromPrimaryMaterialFlow() {
        var result = service.calculateStep(step("鲜牛腩", "10", "修割牛腩", "9"));

        assertThat(result.mainYieldPercent()).isEqualByComparingTo("90.000000");
    }

    @Test
    void calculatesMajorYieldFromItsFirstPrimaryInputAndLastPrimaryOutput() {
        var result = service.calculate(majorWithFlow("10", "8"));

        assertThat(result.primaryInputWeightKg()).isEqualByComparingTo("10.0000");
        assertThat(result.totalInputWeightKg()).isEqualByComparingTo("10.0000");
        assertThat(result.qualifiedOutputWeightKg()).isEqualByComparingTo("8.0000");
        assertThat(result.mainYieldPercent()).isEqualByComparingTo("80.000000");
    }

    @Test
    void balancesEveryTerminalOutputAcrossTheWholeMajorBoundary() {
        var firstOutputId = "FLOW-MAIN-1";
        var first = new ProcessPlan.MinorStep(null, 1, "CUT", "修割", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, "牛肉", "10")), List.of(
                new ProcessPlan.StepOutput(firstOutputId, 1, "INTERMEDIATE", "修割牛肉", "SOLID", new BigDecimal("9"), true, true, null),
                new ProcessPlan.StepOutput("WASTE-1", 2, "WASTE", "修割废料", "SOLID", new BigDecimal("1"), false, false, null)), List.of());
        var second = new ProcessPlan.MinorStep(null, 2, "COOK", "熟制", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "STEP_OUTPUT", firstOutputId, "修割牛肉", "9")), List.of(
                new ProcessPlan.StepOutput("FINISHED-1", 1, "FINISHED", "熟制牛肉", "SOLID", new BigDecimal("8"), true, false, null),
                new ProcessPlan.StepOutput("WASTE-2", 2, "WASTE", "熟制损耗", "SOLID", new BigDecimal("1"), false, false, null)), List.of());

        var result = service.calculate(major(List.of(first, second)));

        assertThat(result.totalOutputWeightKg()).isEqualByComparingTo("10.0000");
        assertThat(result.balanceDifferenceKg()).isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsNullRateWhenBasisIsZero() {
        var result = service.calculate(major(List.of()));

        assertThat(result.mainYieldPercent()).isNull();
        assertThat(result.recoveryPercent()).isNull();
    }

    @Test
    void calculatesBatchYieldByMultiplyingMajorProcessYields() {
        var plan = new ProcessPlan(null, "FORM-1", 1, "DRAFT", List.of(
                connectedMajor(1, null, "10", "9"), connectedMajor(2, "OUT-1", "9", "7.2"), connectedMajor(3, "OUT-2", "7.2", "5.04")), null, false);

        assertThat(service.calculateBatch(plan)).isEqualByComparingTo("50.400000");
    }

    @Test
    void excludesAuxiliaryMaterialsAndProcessWaterFromThePrimaryYieldDenominator() {
        var step = new ProcessPlan.MinorStep(null, 1, "MARINATE", "腌制", "NORMAL", null, null, null,
                null, null, null, null, null, List.of(
                material("PRIMARY", "EXTERNAL", null, "牛肉", "10"),
                material("AUXILIARY", "EXTERNAL", null, "盐", "2"),
                material("PROCESS_WATER", "EXTERNAL", null, "水", "5")),
                List.of(primaryOutput("腌制牛肉", "8")), List.of());

        var result = service.calculateStep(step);

        assertThat(result.primaryInputWeightKg()).isEqualByComparingTo("10.0000");
        assertThat(result.totalInputWeightKg()).isEqualByComparingTo("17.0000");
        assertThat(result.mainYieldPercent()).isEqualByComparingTo("80.000000");
    }

    @Test
    void skipsMajorProcessesWhoseYieldBasisIsNoneWhenCalculatingBatchYield() {
        var skipped = major(List.of());
        skipped = new ProcessPlan.MajorProcess(skipped.id(), skipped.sequence(), skipped.processCode(), skipped.processName(),
                skipped.description(), "NONE", "检查包装", skipped.steps(), skipped.inputs(), skipped.outputs(), skipped.yield());
        var plan = new ProcessPlan(null, "FORM-1", 1, "DRAFT", List.of(skipped, majorWithFlow("10", "8")), null, false);

        assertThat(service.calculateBatch(plan)).isEqualByComparingTo("80.000000");
    }

    @Test
    void withholdsBatchYieldWhenAnyRequiredMajorIsIncomplete() {
        var incomplete = major(List.of(new ProcessPlan.MinorStep(null, 1, "HOLD", "静置", "NORMAL", null, null, null,
                null, null, null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, "牛肉", "10")), List.of(), List.of())));
        var plan = new ProcessPlan(null, "FORM-1", 1, "DRAFT", List.of(incomplete, majorWithFlow("10", "8")), null, false);

        assertThat(service.calculate(incomplete).mainYieldPercent()).isNull();
        assertThat(service.calculateBatch(plan)).isNull();
    }

    @Test
    void returnsNoMajorYieldWhenTheOnlyPrimaryOutputPrecedesThePrimaryInput() {
        var outputBeforeInput = new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", null, "PRIMARY_INPUT", null,
                List.of(
                        new ProcessPlan.MinorStep(null, 1, "PREP", "预处理", "NORMAL", null, null, null,
                                null, null, null, null, null, List.of(), List.of(primaryOutput("预处理产出", "9")), List.of()),
                        new ProcessPlan.MinorStep(null, 2, "ADD", "投料", "NORMAL", null, null, null,
                                null, null, null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, "牛肉", "10")), List.of(), List.of())),
                List.of(), List.of(), null);

        assertThat(service.calculate(outputBeforeInput).mainYieldPercent()).isNull();
    }

    @Test
    void fallsBackToLegacyMajorTotalsWhenAHistoricalStepOnlyContainsAuxiliaryMaterial() {
        var historicalStep = new ProcessPlan.MinorStep(null, 1, "BOIL", "煮制", "NORMAL", null, null, null,
                null, null, null, null, null, List.of(material("AUXILIARY", "EXTERNAL", null, "盐", "0.2")), List.of(), List.of());
        var historicalMajor = new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", null, "PRIMARY_INPUT", null,
                List.of(historicalStep), List.of(new ProcessPlan.ProcessInput(null, 1, "PRIMARY", "BEEF", "牛肉",
                new BigDecimal("10"), null)), List.of(new ProcessPlan.ProcessOutput(null, 1, "QUALIFIED", new BigDecimal("8"), null)), null);

        assertThat(service.calculate(historicalMajor).mainYieldPercent()).isEqualByComparingTo("80.000000");
    }

    @Test
    void rejectsMoreThanOnePrimaryMaterialInAResultBearingStep() {
        var invalid = new ProcessPlan.MinorStep(null, 1, "CUT", "切分", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, "牛肉", "10"),
                material("PRIMARY", "EXTERNAL", null, "鸡肉", "2")), List.of(primaryOutput("切分肉", "8")), List.of());

        assertThatThrownBy(() -> service.calculateStep(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one primary material");
    }

    @Test
    void rejectsMoreThanOnePrimaryOutputInAResultBearingStep() {
        var invalid = new ProcessPlan.MinorStep(null, 1, "CUT", "切分", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, "牛肉", "10")),
                List.of(primaryOutput("切分肉", "8"), primaryOutput("另一个主料产出", "1")), List.of());

        assertThatThrownBy(() -> service.calculateStep(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one primary output");
    }

    private ProcessPlan.MajorProcess connectedMajor(int sequence, String source, String input, String output) {
        var step = new ProcessPlan.MinorStep(null, 1, "COOK", "熟制", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", source == null ? "EXTERNAL" : "STEP_OUTPUT", source, "牛肉", input)),
                List.of(new ProcessPlan.StepOutput("OUT-" + sequence, 1, "INTERMEDIATE", "牛肉", "SOLID", new BigDecimal(output), true, true, null)), List.of());
        return new ProcessPlan.MajorProcess(null, sequence, "HEAT", "热加工", null, "PRIMARY_INPUT", null, List.of(step), List.of(), List.of(), null);
    }

    private ProcessPlan.MajorProcess majorWithFlow(String inputWeight, String outputWeight) {
        var firstOutputId = "OUTPUT-FIRST";
        var first = step("鲜牛腩", inputWeight, "修割牛腩", inputWeight, firstOutputId);
        var last = new ProcessPlan.MinorStep(null, 2, "COOK", "熟制", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "STEP_OUTPUT", firstOutputId, "修割牛腩", inputWeight)),
                List.of(primaryOutput("熟制牛腩", outputWeight)), List.of());
        return major(List.of(first, last));
    }

    private ProcessPlan.MajorProcess major(List<ProcessPlan.MinorStep> steps) {
        return new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", null,
                "PRIMARY_INPUT", null, steps, List.of(), List.of(), null);
    }

    private ProcessPlan.MinorStep step(String inputName, String inputWeight, String outputName, String outputWeight) {
        return step(inputName, inputWeight, outputName, outputWeight, "OUTPUT-1");
    }

    private ProcessPlan.MinorStep step(String inputName, String inputWeight, String outputName, String outputWeight, String outputId) {
        return new ProcessPlan.MinorStep(null, 1, "TRIM", "修割", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(material("PRIMARY", "EXTERNAL", null, inputName, inputWeight)),
                List.of(new ProcessPlan.StepOutput(outputId, 1, "INTERMEDIATE", outputName, "SOLID", new BigDecimal(outputWeight),
                        true, true, null)), List.of());
    }

    private ProcessPlan.StepMaterial material(String role, String sourceType, String sourceOutputId, String name, String weight) {
        return new ProcessPlan.StepMaterial(null, 1, role, null, name, "SOLID", new BigDecimal(weight), null, null,
                sourceType, sourceOutputId);
    }

    private ProcessPlan.StepOutput primaryOutput(String name, String weight) {
        return new ProcessPlan.StepOutput(null, 1, "INTERMEDIATE", name, "SEMI_SOLID", new BigDecimal(weight), true, true, null);
    }
}
