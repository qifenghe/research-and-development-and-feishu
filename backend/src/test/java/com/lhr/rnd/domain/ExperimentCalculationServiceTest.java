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

        assertThat(result.lossWeight()).isEqualTo(new BigDecimal("1.5000"));
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

        assertThat(result.lossWeight()).isEqualTo(new BigDecimal("2.0000"));
        assertThat(result.lossRate()).isEqualTo(new BigDecimal("0.200000"));
    }

    @Test
    void returnsNoLossRateWhenInputWeightIsZero() {
        var result = service.processLoss(BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThat(result.lossWeight()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.lossRate()).isNull();
    }

    @Test
    void returnsNoFinishedYieldWhenPrimaryRawMaterialInputIsZero() {
        assertThat(service.finishedYield(new BigDecimal("10"), BigDecimal.ZERO)).isNull();
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
