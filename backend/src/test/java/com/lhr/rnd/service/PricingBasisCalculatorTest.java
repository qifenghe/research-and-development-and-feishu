package com.lhr.rnd.service;

import com.lhr.rnd.model.PricingBasisDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PricingBasisCalculatorTest {
    final PricingBasisCalculator calculator = new PricingBasisCalculator();
    static BigDecimal n(String value) { return value == null ? null : new BigDecimal(value); }
    static PricingBasisDraft.Material material(String mode, String original, String usage, String rate) {
        return new PricingBasisDraft.Material("row-1", "m1", "s1", "material-1", "清洗", "001", "大肠头",
                "PRIMARY", "EXTERNAL", n(original), mode, n(usage), n(rate), "output-1", "研发确认净用量", null);
    }
    static PricingBasisDraft draft(PricingBasisDraft.Material material, String finished) {
        return new PricingBasisDraft(1, "revision-1", null, List.of(material), n("89"), List.of("row-1"),
                "确认主料范围", n(finished), n("0.5"), 20, null, List.of());
    }
    @Test void afterProcessUsesUtilizationOnce() {
        var result = calculator.calculate(draft(material("AFTER_PROCESS", "100", "100", "0.95"), "89"));
        assertThat(result.issues()).isEmpty();
        assertThat(result.materials().get(0).requisitionKg()).isEqualByComparingTo("105.263157894737");
        assertThat(result.mainYieldPercent().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("84.55");
    }
    @Test void beforeProcessDoesNotDeductAgain() {
        var result = calculator.calculate(draft(material("BEFORE_PROCESS", "100", "95", null), "89"));
        assertThat(result.issues()).isEmpty();
        assertThat(result.materials().get(0).requisitionKg()).isEqualByComparingTo("100");
        assertThat(result.materials().get(0).utilizationRate()).isEqualByComparingTo("0.95");
    }
    @Test void invalidRatesNeverBecomeValidTotals() {
        for (String rate : new String[]{null, "0", "-0.1", "1.01"}) {
            var result = calculator.calculate(draft(material("AFTER_PROCESS", "100", "100", rate), "89"));
            assertThat(result.issues()).isNotEmpty();
            assertThat(result.requisitionTotalKg()).isNull();
            assertThat(result.mainYieldPercent()).isNull();
        }
    }
    @Test void packingUsesIndependentNetWeight() {
        var result = calculator.calculate(draft(material("AFTER_PROCESS", "100", "100", "1"), "89.2"));
        assertThat(result.referenceBags()).isEqualTo(178L);
        assertThat(result.referenceBoxes()).isEqualTo(9L);
        assertThat(result.remainingKg()).isEqualByComparingTo("0.2");
        var absent = calculator.calculate(draft(material("AFTER_PROCESS", "100", "100", "1"), null));
        assertThat(absent.referenceBags()).isNull();
        assertThat(absent.issues()).anyMatch(i -> i.path().equals("finishedNetKg"));
    }
    @Test void missingAndZeroUsageRemainDistinct() {
        var zero = calculator.calculate(draft(material("AFTER_PROCESS", "0", "0", "1"), "0"));
        assertThat(zero.materials().get(0).requisitionKg()).isEqualByComparingTo("0");
        assertThat(zero.mainYieldPercent()).isNull();
        var absent = calculator.calculate(draft(material("AFTER_PROCESS", "0", null, "1"), "0"));
        assertThat(absent.materials().get(0).requisitionKg()).isNull();
    }
    @Test void rejectsUnattributedOutputsAndNonExternalOrDuplicateRows() {
        var m = material("BEFORE_PROCESS", "100", "95", null);
        var noBasis = new PricingBasisDraft.Material(m.id(),m.majorId(),m.stepId(),m.sourceMaterialId(),m.stage(),m.materialCode(),m.materialName(),m.role(),m.sourceType(),m.originalWeightKg(),m.weightBasis(),m.usageKg(),null,null,null,null);
        assertThat(calculator.calculate(draft(noBasis,"89")).issues()).anyMatch(i -> i.path().endsWith("confirmationBasis"));
        var d = draft(m,"89");
        var duplicate = new PricingBasisDraft(1,d.sourceRevisionId(),null,List.of(m,m),d.mainOutputKg(),List.of("missing"),null,d.finishedNetKg(),d.unitWeightKg(),20,null,List.of());
        assertThat(calculator.calculate(duplicate).issues()).anyMatch(i -> i.path().endsWith(".id"));
        assertThat(calculator.calculate(duplicate).mainYieldPercent()).isNull();
        var intermediate = new PricingBasisDraft.Material(m.id(),m.majorId(),m.stepId(),m.sourceMaterialId(),m.stage(),m.materialCode(),m.materialName(),m.role(),"STEP_OUTPUT",m.originalWeightKg(),m.weightBasis(),m.usageKg(),null,null,"依据",null);
        assertThat(calculator.calculate(draft(intermediate,"89")).requisitionTotalKg()).isNull();
        assertThat(calculator.calculate(draft(material("AFTER_PROCESS","100","-1","1"),"89")).issues()).isNotEmpty();
    }
    @Test void packagingRemarksAreNotRatesAndInvalidQuantityIsReported() {
        var d = draft(material("AFTER_PROCESS","100","100","1"),"89");
        var packing = new PricingBasisDraft.Packaging("p","code","袋","003",n("178"),"个","500g/袋",null,"0.99",null);
        var withPacking = new PricingBasisDraft(1,d.sourceRevisionId(),null,d.materials(),d.mainOutputKg(),d.mainMaterialIds(),d.mainScopeBasis(),d.finishedNetKg(),d.unitWeightKg(),20,null,List.of(packing));
        assertThat(calculator.calculate(withPacking).referenceBags()).isEqualTo(178L);
        var invalid = new PricingBasisDraft.Packaging("p","code","袋","003",n("-1"),"个","500g/袋",null,"0.99",null);
        var bad = new PricingBasisDraft(1,d.sourceRevisionId(),null,d.materials(),d.mainOutputKg(),d.mainMaterialIds(),d.mainScopeBasis(),d.finishedNetKg(),d.unitWeightKg(),20,null,List.of(invalid));
        assertThat(calculator.calculate(bad).issues()).anyMatch(i -> i.path().equals("packaging[0].quantity"));
    }
}
