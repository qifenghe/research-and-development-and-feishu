package com.lhr.rnd.api;

import com.lhr.rnd.model.CustomerFeedbackResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitCustomerFeedbackRequest(
        @NotBlank String feedbackBy,
        @NotNull CustomerFeedbackResult result,
        String comment
) {
}
