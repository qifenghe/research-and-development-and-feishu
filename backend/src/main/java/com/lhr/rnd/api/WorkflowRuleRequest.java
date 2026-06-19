package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record WorkflowRuleRequest(
        @NotBlank String currentStatus,
        @NotBlank String actionCode,
        @NotBlank String actionLabel,
        @NotBlank String nextStatus,
        boolean enabled,
        boolean notifyFeishu,
        String notifyRole,
        int sortOrder,
        String remark
) {
}
