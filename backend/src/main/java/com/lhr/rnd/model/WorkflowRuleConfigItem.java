package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record WorkflowRuleConfigItem(
        String id,
        String workflowCode,
        String currentStatus,
        String actionCode,
        String actionLabel,
        String nextStatus,
        boolean enabled,
        boolean notifyFeishu,
        String notifyRole,
        int sortOrder,
        String remark,
        LocalDateTime updatedAt
) {
}
