package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record NotifyFinanceRequest(
        @NotBlank String recipientName,
        String remark
) {
}
