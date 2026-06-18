package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record CustomerFeedback(
        String id,
        String shipmentId,
        String feedbackBy,
        CustomerFeedbackResult result,
        String comment,
        LocalDateTime feedbackAt
) {
}
