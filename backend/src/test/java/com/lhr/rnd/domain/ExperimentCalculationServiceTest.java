package com.lhr.rnd.domain;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.YieldCalculationMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentCalculationServiceTest {
    private final ExperimentCalculationService service = new ExperimentCalculationService();

    @Test
    void calculatesProcessLossAfterRemovingResidualMaterial() {
        var result = service.processLoss(
                new BigDecimal("10"),
                new BigDecimal("8"),
                new BigDecimal("0.5"));

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("1.5000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.150000"));
    }

    @Test
    void calculatesFinishedYieldPercentAsFinishedProductOutputOverPrimaryRawMaterialInput() {
        var finishedProductOutput = new BigDecimal("10");
        var primaryRawMaterialInput = new BigDecimal("12.5");

        assertThat(service.finishedYield(finishedProductOutput, primaryRawMaterialInput))
                .isEqualTo(new BigDecimal("80.000000"));
    }

    @Test
    void calculatesPricingPreviewForFinishedBags() {
        var preview = service.pricingPreview(
                new BigDecimal("10"),
                new BigDecimal("2"),
                new BigDecimal("8.5"),
                new BigDecimal("17"));

        assertThat(preview.totalInputWeightKg()).isEqualTo(new BigDecimal("12.0000"));
        assertThat(preview.primaryMaterialYieldPercent()).isEqualTo(new BigDecimal("85.000000"));
        assertThat(preview.averageUnitWeightKg()).isEqualTo(new BigDecimal("0.5000"));
        assertThat(preview.referenceQuantity()).isEqualTo(new BigDecimal("17.0000"));
    }

    @Test
    void calculatesProcessLossRateAfterResidualMaterial() {
        var result = service.processLoss(
                new BigDecimal("10"),
                new BigDecimal("8"),
                new BigDecimal("1"));

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.100000"));
    }

    @Test
    void roundsPricingPreviewResultsOnlyAfterUsingPreciseOperands() {
        var preview = service.pricingPreview(
                new BigDecimal("1.00000"),
                new BigDecimal("0.00004"),
                new BigDecimal("1.00004"),
                BigDecimal.ONE);

        assertThat(preview.totalInputWeightKg()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(preview.primaryMaterialYieldPercent()).isEqualTo(new BigDecimal("100.004000"));
        assertThat(preview.averageUnitWeightKg()).isEqualTo(new BigDecimal("1.0000"));
    }

    @Test
    void treatsMissingResidualWeightAsZero() {
        var result = service.processLoss(new BigDecimal("10"), new BigDecimal("8"), null);

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("2.0000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.200000"));
    }

    @Test
    void returnsEmptyProcessLossWhenRequiredDraftWeightsAreMissing() {
        assertThat(service.processLoss(null, new BigDecimal("8"), null))
                .isEqualTo(new ExperimentCalculationService.ProcessLoss(null, null));
        assertThat(service.processLoss(new BigDecimal("10"), null, null))
                .isEqualTo(new ExperimentCalculationService.ProcessLoss(null, null));
    }

    @Test
    void returnsNoFinishedYieldWhenFinishedProductOutputIsMissing() {
        assertThat(service.finishedYield(null, new BigDecimal("12.5"))).isNull();
    }

    @Test
    void returnsNoLossRateWhenInputWeightIsZero() {
        var result = service.processLoss(BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.lossRate()).isNull();
    }

    @Test
    void returnsNoFinishedYieldWhenPrimaryRawMaterialInputIsZero() {
        assertThat(service.finishedYield(new BigDecimal("10"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void rejectsNegativeProcessWeights() {
        assertThatThrownBy(() -> service.processLoss(new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputWeight must not be negative");
        assertThatThrownBy(() -> service.processLoss(BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outputWeight must not be negative");
        assertThatThrownBy(() -> service.processLoss(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("residualWeight must not be negative");
        assertThatThrownBy(() -> service.processLoss(new BigDecimal("-0.00001"), BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputWeight must not be negative");
    }

    @Test
    void rejectsNegativeFinishedYieldWeights() {
        assertThatThrownBy(() -> service.finishedYield(new BigDecimal("-1"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishedProductOutput must not be negative");
        assertThatThrownBy(() -> service.finishedYield(BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("primaryRawMaterialInput must not be negative");
    }

    @Test
    void roundsProcessLossOnlyAfterCalculatingWithPreciseWeights() {
        var result = service.processLoss(new BigDecimal("1.00004"), new BigDecimal("1.00000"), null);

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.000040"));
    }

    @Test
    void roundsFinishedYieldPercentOnlyAfterCalculatingWithPreciseWeights() {
        assertThat(service.finishedYield(new BigDecimal("1.00004"), new BigDecimal("1.00000")))
                .isEqualTo(new BigDecimal("100.004000"));
    }

    @Test
    void rejectsOutputAndResidualThatExceedInput() {
        assertThatThrownBy(() -> service.processLoss(
                new BigDecimal("10"),
                new BigDecimal("9"),
                new BigDecimal("2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Output plus residual weight cannot exceed input weight");
    }

    @Test
    void calculatesYieldBasisFromMultipleSelectedPrimaryPickingWeights() {
        var materials = List.of(
                material("猪肉", "RAW", true, "95", "0.95"),
                material("鸡肉", "RAW", true, "50", "1"),
                material("盐", "AUXILIARY", false, "5", "1"));

        assertThat(service.yieldBasisWeight(materials, YieldCalculationMode.SELECTED_PRIMARY_MATERIALS))
                .isEqualByComparingTo("150.000000");
    }

    @Test
    void calculatesSauceYieldBasisFromAllNonPackagingPickingWeights() {
        var materials = List.of(
                material("水", "RAW", false, "100", "1"),
                material("香辛料", "AUXILIARY", false, "10", "0.5"),
                material("包装袋", "PACKAGING", false, "3", "1"));

        assertThat(service.yieldBasisWeight(materials, YieldCalculationMode.TOTAL_PICKING_WEIGHT))
                .isEqualByComparingTo("120.000000");
    }

    private ExperimentMaterial material(
            String name,
            String category,
            boolean primary,
            String weight,
            String utilizationRate
    ) {
        return new ExperimentMaterial(
                category,
                1,
                null,
                name,
                new BigDecimal(weight),
                new BigDecimal(utilizationRate),
                null,
                category,
                primary,
                null,
                "kg");
    }
}
