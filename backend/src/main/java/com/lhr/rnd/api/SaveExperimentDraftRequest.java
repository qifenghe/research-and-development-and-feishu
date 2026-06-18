package com.lhr.rnd.api;

import com.lhr.rnd.model.ExperimentMaterial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SaveExperimentDraftRequest(
        @NotBlank String operatorName,
        String summary,
        @Valid List<ExperimentMaterial> materials
) {
}
