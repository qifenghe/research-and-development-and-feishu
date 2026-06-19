package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record FeishuCardActionRequest(
        @NotBlank String action,
        @NotBlank String businessType,
        @NotBlank String businessId,
        @NotBlank String feishuUserId
) {
}
