package com.lhr.rnd.service;

import com.lhr.rnd.model.WorkflowRuleConfigItem;
import com.lhr.rnd.persistence.entity.WorkflowRuleConfigEntity;
import com.lhr.rnd.persistence.repository.WorkflowRuleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowSettingsService {
    private final WorkflowRuleConfigRepository repository;

    public WorkflowSettingsService(WorkflowRuleConfigRepository repository) {
        this.repository = repository;
    }

    public WorkflowConfig workflow(String workflowCode) {
        var normalizedWorkflowCode = normalizeCode(workflowCode);
        return new WorkflowConfig(
                normalizedWorkflowCode,
                repository.findByWorkflowCodeOrderBySortOrderAsc(normalizedWorkflowCode).stream()
                        .map(WorkflowRuleConfigEntity::toModel)
                        .toList()
        );
    }

    public List<WorkflowConfig> workflows() {
        var grouped = new LinkedHashMap<String, List<WorkflowRuleConfigItem>>();
        repository.findAllByOrderByWorkflowCodeAscSortOrderAsc().stream()
                .map(WorkflowRuleConfigEntity::toModel)
                .forEach(rule -> grouped.computeIfAbsent(rule.workflowCode(), ignored -> new ArrayList<>()).add(rule));
        return grouped.entrySet().stream()
                .map(entry -> new WorkflowConfig(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Transactional
    public WorkflowConfig replaceWorkflow(String workflowCode, List<WorkflowRule> rules) {
        var normalizedWorkflowCode = normalizeCode(workflowCode);
        repository.deleteByWorkflowCode(normalizedWorkflowCode);
        return saveWorkflow(normalizedWorkflowCode, rules, LocalDateTime.now());
    }

    @Transactional
    public List<WorkflowConfig> initializeDefaultWorkflows() {
        return defaultWorkflows().stream()
                .map(config -> {
                    if (repository.countByWorkflowCode(config.workflowCode()) == 0) {
                        var rules = config.rules().stream()
                                .map(rule -> new WorkflowRule(
                                        rule.currentStatus(),
                                        rule.actionCode(),
                                        rule.actionLabel(),
                                        rule.nextStatus(),
                                        rule.enabled(),
                                        rule.notifyFeishu(),
                                        rule.notifyRole(),
                                        rule.sortOrder(),
                                        rule.remark()
                                ))
                                .toList();
                        return saveWorkflow(config.workflowCode(), rules, LocalDateTime.now());
                    }
                    return workflow(config.workflowCode());
                })
                .toList();
    }

    private WorkflowConfig saveWorkflow(String workflowCode, List<WorkflowRule> rules, LocalDateTime now) {
        var saved = rules.stream()
                .map(rule -> repository.save(new WorkflowRuleConfigEntity(
                        "FLOW-" + UUID.randomUUID().toString().replace("-", "").substring(0, 23),
                        workflowCode,
                        normalizeCode(rule.currentStatus()),
                        normalizeCode(rule.actionCode()),
                        rule.actionLabel(),
                        normalizeCode(rule.nextStatus()),
                        rule.enabled(),
                        rule.notifyFeishu(),
                        normalizeOptionalCode(rule.notifyRole()),
                        rule.sortOrder(),
                        rule.remark(),
                        now
                )))
                .map(WorkflowRuleConfigEntity::toModel)
                .sorted(Comparator.comparingInt(WorkflowRuleConfigItem::sortOrder))
                .toList();
        return new WorkflowConfig(workflowCode, saved);
    }

    private List<WorkflowConfig> defaultWorkflows() {
        return List.of(new WorkflowConfig("SAMPLE_RND_FLOW", List.of(
                item("PENDING_REVIEW", "APPROVE_REQUEST", "审核通过", "PENDING_ASSIGNMENT", true, "RND_DIRECTOR", 10, "研发总监审核需求"),
                item("PENDING_ASSIGNMENT", "ASSIGN_TASK", "分发任务", "PENDING_ACCEPTANCE", true, "RND_ENGINEER", 20, "通知研发人员接收任务"),
                item("PENDING_ACCEPTANCE", "ACCEPT_TASK", "接受任务", "SAMPLING", false, null, 30, "研发人员接受任务"),
                item("SAMPLING", "SUBMIT_EXPERIMENT", "提交测试", "PENDING_TEST", true, "TESTER", 40, "通知内部测试人员"),
                item("PENDING_TEST", "TEST_PASS", "测试通过", "SAMPLE_COMPLETED", true, "RND_ASSISTANT", 50, "进入寄样/核价阶段"),
                item("PENDING_TEST", "TEST_FAIL_RESAMPLE", "复打样", "RESAMPLING_REQUIRED", true, "RND_ENGINEER", 60, "测试不通过进入复打样"),
                item("RESAMPLING_REQUIRED", "CREATE_NEXT_VERSION", "生成下一版", "PENDING_ACCEPTANCE", true, "RND_ENGINEER", 70, "生成 A1/A2/A3 等下一版本"),
                item("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_PASS", "客户通过", "SAMPLE_COMPLETED", true, "RND_ASSISTANT", 80, "客户确认样品通过"),
                item("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_RESAMPLE", "客户复打样", "RESAMPLING_REQUIRED", true, "RND_ENGINEER", 90, "客户反馈不通过，进入复打样"),
                item("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_STOP", "客户停止", "STOPPED", true, "RND_DIRECTOR", 100, "客户或业务确认停止打样"),
                item("SAMPLE_COMPLETED", "REQUEST_PRICING", "生成核价", "PRICING_FILE_GENERATED", true, "FINANCE", 110, "生成核价文件"),
                item("PRICING_FILE_GENERATED", "NOTIFY_FINANCE", "通知财务", "FINANCE_NOTIFIED", true, "FINANCE", 120, "飞书通知财务核价"),
                item("FINANCE_NOTIFIED", "ARCHIVE", "归档", "ARCHIVED", false, null, 130, "流程归档")
        )));
    }

    private WorkflowRuleConfigItem item(
            String currentStatus,
            String actionCode,
            String actionLabel,
            String nextStatus,
            boolean notifyFeishu,
            String notifyRole,
            int sortOrder,
            String remark
    ) {
        return new WorkflowRuleConfigItem(
                null,
                "SAMPLE_RND_FLOW",
                currentStatus,
                actionCode,
                actionLabel,
                nextStatus,
                true,
                notifyFeishu,
                notifyRole,
                sortOrder,
                remark,
                null
        );
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String normalizeOptionalCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
}
