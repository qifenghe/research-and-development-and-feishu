package com.lhr.rnd.api;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.ExperimentProcessStep;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record SaveExperimentDraftRequest(
        @NotBlank String operatorName,
        String summary,
        @Valid List<ExperimentMaterial> materials,
        @Valid List<ExperimentProcessStep> processSteps,
        BigDecimal finishedOutputWeightKg,
        @Positive Integer finishedOutputQuantity,
        @Pattern(regexp = "\\s*|袋|盒|份|个|盘") String finishedOutputUnit,
        BigDecimal finishedYieldRatio
) {
}
