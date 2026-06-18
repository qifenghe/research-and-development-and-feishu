package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record ApproveSampleRequestRequest(@NotBlank String reviewerName) {
}
