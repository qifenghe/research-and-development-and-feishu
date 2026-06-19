package com.lhr.rnd.service;

public record WorkflowRule(
        String currentStatus,
        String actionCode,
        String actionLabel,
        String nextStatus,
        boolean enabled,
        boolean notifyFeishu,
        String notifyRole,
        int sortOrder,
        String remark
) {
}
