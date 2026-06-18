package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record InternalTestDecisionRequest(@NotBlank String testerName, String comment) {
}
