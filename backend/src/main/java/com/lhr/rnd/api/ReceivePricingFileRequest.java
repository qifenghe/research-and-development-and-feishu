package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record ReceivePricingFileRequest(
        @NotBlank(message = "接收人不能为空") String receivedBy
) {
}
