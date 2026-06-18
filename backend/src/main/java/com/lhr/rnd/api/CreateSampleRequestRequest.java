package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSampleRequestRequest(
        @NotBlank String productName,
        @NotBlank String productType,
        @NotBlank String customerName,
        @NotBlank String specification,
        @NotBlank String creatorName
) {
}
