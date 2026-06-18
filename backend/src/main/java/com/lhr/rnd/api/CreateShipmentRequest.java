package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateShipmentRequest(
        @NotNull @Positive Integer quantity,
        @NotBlank String receiverName,
        @NotBlank String trackingNo,
        String remark
) {
}
