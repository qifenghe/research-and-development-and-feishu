package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.WorkflowRuleConfigItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_rule_config")
public class WorkflowRuleConfigEntity {
    @Id
    private String id;

    @Column(name = "workflow_code", nullable = false)
    private String workflowCode;

    @Column(name = "current_status", nullable = false)
    private String currentStatus;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "action_label", nullable = false)
    private String actionLabel;

    @Column(name = "next_status", nullable = false)
    private String nextStatus;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notify_feishu", nullable = false)
    private boolean notifyFeishu;

    @Column(name = "notify_role")
    private String notifyRole;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "remark")
    private String remark;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkflowRuleConfigEntity() {
    }

    public WorkflowRuleConfigEntity(
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
        this.id = id;
        this.workflowCode = workflowCode;
        this.currentStatus = currentStatus;
        this.actionCode = actionCode;
        this.actionLabel = actionLabel;
        this.nextStatus = nextStatus;
        this.enabled = enabled;
        this.notifyFeishu = notifyFeishu;
        this.notifyRole = notifyRole;
        this.sortOrder = sortOrder;
        this.remark = remark;
        this.updatedAt = updatedAt;
    }

    public WorkflowRuleConfigItem toModel() {
        return new WorkflowRuleConfigItem(
                id,
                workflowCode,
                currentStatus,
                actionCode,
                actionLabel,
                nextStatus,
                enabled,
                notifyFeishu,
                notifyRole,
                sortOrder,
                remark,
                updatedAt
        );
    }
}
