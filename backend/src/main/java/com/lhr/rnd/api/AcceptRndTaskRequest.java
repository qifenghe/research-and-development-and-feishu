package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record AcceptRndTaskRequest(@NotBlank String acceptedBy) {
}
