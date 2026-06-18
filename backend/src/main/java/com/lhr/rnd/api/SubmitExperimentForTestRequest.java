package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record SubmitExperimentForTestRequest(@NotBlank String testerName) {
}
