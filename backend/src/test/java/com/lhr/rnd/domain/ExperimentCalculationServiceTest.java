package com.lhr.rnd.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
    void calculatesFinishedYieldAsFinishedProductOutputOverPrimaryRawMaterialInput() {
        var finishedProductOutput = new BigDecimal("10");
        var primaryRawMaterialInput = new BigDecimal("12.5");

        assertThat(service.finishedYield(finishedProductOutput, primaryRawMaterialInput))
                .isEqualTo(new BigDecimal("0.800000"));
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
    void normalizesProcessWeightsToDatabaseScaleBeforeCalculating() {
        var result = service.processLoss(new BigDecimal("1.00004"), new BigDecimal("1.00000"), null);

        assertThat(result.lossWeightKg()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void normalizesFinishedYieldWeightsToDatabaseScaleBeforeCalculating() {
        assertThat(service.finishedYield(new BigDecimal("1.00004"), new BigDecimal("1.00000")))
                .isEqualTo(new BigDecimal("1.000000"));
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
}
