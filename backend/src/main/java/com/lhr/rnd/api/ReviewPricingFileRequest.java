package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record ReviewPricingFileRequest(
        @NotBlank String decision,
        String comment
) {
}
