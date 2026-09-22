package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProcessExportCheckServiceTest {
    private final ProcessExportCheckService service = new ProcessExportCheckService();

    @Test void measuresActualExternalMassAndPrimaryChainWithoutCountingIntermediateTwice() {
        var view = view(plan("72", "90", "PRIMARY_INPUT"));
        assertThat(view.externalInputKg()).isEqualByComparingTo("102");
        assertThat(view.mainYieldPercent()).isEqualByComparingTo("72");
        assertThat(view.majors()).extracting(m -> m.mainYieldPercent().stripTrailingZeros().toPlainString()).containsExactly("90", "80");
        assertThat(view.finishedQuantity().weightKg()).isEqualByComparingTo("74");
        assertThat(service.check(view, "FORMULA_XLSX").ready()).isTrue();
    }

    @Test void genuineTerminalZeroIsZeroButMissingOrBrokenIntermediateMakesMajorAndBatchPending() {
        assertThat(view(plan("0", "90", "PRIMARY_INPUT")).mainYieldPercent()).isEqualByComparingTo("0");
        assertThat(view(plan(null, "90", "PRIMARY_INPUT")).mainYieldPercent()).isNull();
        var missing = view(plan("72", null, "PRIMARY_INPUT"));
        assertThat(missing.mainYieldPercent()).isNull();
        assertThat(missing.majors().get(1).mainYieldPercent()).isNull();
        assertThat(service.check(missing, "FORMULA_XLSX").ready()).isFalse();
        assertThat(service.check(view(plan("72", "89", "PRIMARY_INPUT")), "FORMULA_XLSX").issues())
                .anyMatch(i -> i.code().equals("FLOW_WEIGHT_MISMATCH") && i.majorSequence() == 2);
    }

    @Test void legacyTotalInputIsPendingAndNoneDoesNotParticipate() {
        var legacy = view(plan("72", "90", "TOTAL_INPUT"));
        assertThat(legacy.mainYieldPercent()).isNull();
        assertThat(service.check(legacy, "FORMULA_XLSX").issues()).anyMatch(i -> i.code().equals("UNSUPPORTED_YIELD_BASIS"));
        assertThat(view(plan("90", "90", "NONE")).mainYieldPercent()).isEqualByComparingTo("90");
    }

    @Test void missingMiddleObservationWithinOneMajorCannotProduceACompleteFirstToLastYield() {
        var start = step("S1", material("BEEF", "牛肉", "PRIMARY", "100", "EXTERNAL", null), "O1", "90", true);
        var middle = step("S2", material(null, "中间一", "PRIMARY", "90", "STEP_OUTPUT", "O1"), "O2", null, true);
        var end = step("S3", material(null, "中间二", "PRIMARY", null, "STEP_OUTPUT", "O2"), "O3", "72", false);
        var steps = java.util.stream.IntStream.range(0, 3).mapToObj(i -> {
            var s = List.of(start, middle, end).get(i);
            return new ProcessPlan.MinorStep(s.id(), i + 1, s.stepCode(), s.stepName(), s.stepType(), null, null, null, null, null, null, s.equipment(), s.instruction(), s.materials(), s.outputs(), s.controlPoints());
        }).toList();
        var p = new ProcessPlan("P", "F", 1, "DRAFT", List.of(new ProcessPlan.MajorProcess("M", 1, "COOK", "熟制", null, "PRIMARY_INPUT", null, steps, List.of(), List.of(), null)), null, false);
        assertThat(view(p).majors().get(0).mainYieldPercent()).isNull();
        assertThat(view(p).mainYieldPercent()).isNull();
    }

    @Test void pricingRequiresExplicitPackagingUnitAndIndependentFinishedBasisButNoPriceOrLegacyMaterial() {
        var view = view(plan("72", "90", "PRIMARY_INPUT"));
        assertThat(service.check(view, "PRICING_XLSX").ready()).isTrue();
        var missing = service.view("测试产品", "方案A / V1", plan("72", "90", "PRIMARY_INPUT"), null,
                List.of(new PricingPackagingItem("p", "f", 1, PricingPackagingSource.MANUAL, null, "袋", BigDecimal.ONE, null, null, null, PricingPackagingStatus.CONFIRMED, null)));
        assertThat(service.check(missing, "PRICING_XLSX").issues()).extracting(ProcessExportView.Issue::code)
                .contains("PACKAGING_UNIT_REQUIRED", "FINISHED_QUANTITY_REQUIRED");
    }

    @Test void sopRequiresInstructionsButFormulaDoesNot() {
        var p = plan("72", "90", "PRIMARY_INPUT");
        var major = p.majorProcesses().get(0); var step = major.steps().get(0);
        var blank = new ProcessPlan.MinorStep(step.id(), 1, step.stepCode(), step.stepName(), "NORMAL", null, null, null, null, null, null, null, null, step.materials(), step.outputs(), List.of());
        var changed = new ProcessPlan.MajorProcess(major.id(), 1, "M1", "前处理", null, "PRIMARY_INPUT", null, List.of(blank), List.of(), List.of(), null);
        var view = view(new ProcessPlan("P", "F", 1, "DRAFT", List.of(changed, p.majorProcesses().get(1)), null, false));
        assertThat(service.check(view, "SOP_DOCX").issues()).anyMatch(i -> i.code().equals("STEP_INSTRUCTION_REQUIRED") && i.path().contains("steps"));
        assertThat(service.check(view, "FORMULA_XLSX").ready()).isTrue();
    }

    static ProcessPlan plan(String end, String carried, String basis) {
        var first = step("S1", material("BEEF", "牛肉", "PRIMARY", "100", "EXTERNAL", null), "O1", "90", true);
        var second = step("S2", material(null, "中间牛肉", "PRIMARY", carried, "STEP_OUTPUT", "O1"), "O2", end, false);
        var withAux = new ProcessPlan.MinorStep(second.id(), 1, "COOK", "熟制", "NORMAL", null, null, null, null, null, null, "锅", "软件测试操作说明", List.of(second.materials().get(0), material("SALT", "盐", "AUXILIARY", "2", "EXTERNAL", null)), second.outputs(), List.of());
        return new ProcessPlan("P", "F", 1, "DRAFT", List.of(
                new ProcessPlan.MajorProcess("M1", 1, "PREP", "前处理", null, "PRIMARY_INPUT", null, List.of(first), List.of(), List.of(), null),
                new ProcessPlan.MajorProcess("M2", 2, "COOK", "熟制", null, basis, null, List.of(withAux), List.of(), List.of(), null)), new BigDecimal("99"), false);
    }
    private static ProcessPlan.MinorStep step(String id, ProcessPlan.StepMaterial material, String output, String weight, boolean flow) {
        return new ProcessPlan.MinorStep(id, 1, id, id, "NORMAL", null, null, null, null, null, null, "设备", "软件测试操作说明", List.of(material), List.of(new ProcessPlan.StepOutput(output, 1, flow ? "INTERMEDIATE" : "FINISHED", "主料产出", "SOLID", decimal(weight), true, flow, null)), List.of());
    }
    private static ProcessPlan.StepMaterial material(String code, String name, String role, String weight, String source, String output) {
        return new ProcessPlan.StepMaterial(name, 1, role, code, name, "SOLID", decimal(weight), code, null, source, output);
    }
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    static ProcessExportView view(ProcessPlan plan) {
        return new ProcessExportCheckService().view("软件测试牛肉", "正式工艺 R1 / 方案A V3", plan,
                new ProcessExportView.FinishedQuantity(new BigDecimal("74"), 74, "袋", "已锁定实验单独立实测"),
                List.of(new PricingPackagingItem("p", "f", 1, PricingPackagingSource.MANUAL, "BAG", "内袋", new BigDecimal("74"), "1kg", "1个/袋", null, PricingPackagingStatus.CONFIRMED, null, "个")));
    }
}
