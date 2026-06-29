package com.lhr.rnd.service;

import com.lhr.rnd.persistence.repository.RolePermissionRepository;
import com.lhr.rnd.persistence.entity.RolePermissionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RolePermissionService {
    private final RolePermissionRepository repository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RolePermissionService(RolePermissionRepository repository) {
        this.repository = repository;
    }

    public RolePermissionConfig rolePermissions(String roleCode) {
        return new RolePermissionConfig(
                roleCode,
                repository.findByRoleCodeOrderBySortOrderAsc(roleCode).stream()
                        .map(this::toRule)
                        .toList()
        );
    }

    @Transactional
    public RolePermissionConfig replaceRolePermissions(String roleCode, List<RolePermissionRule> permissions) {
        repository.deleteByRoleCode(roleCode);
        return saveRolePermissions(roleCode, permissions, LocalDateTime.now());
    }

    @Transactional
    public List<RolePermissionConfig> initializeDefaultPermissions() {
        return defaultPermissionConfigs().stream()
                .map(config -> {
                    if (repository.countByRoleCode(config.roleCode()) == 0) {
                        return saveRolePermissions(config.roleCode(), config.permissions(), LocalDateTime.now());
                    }
                    return rolePermissions(config.roleCode());
                })
                .toList();
    }

    private RolePermissionConfig saveRolePermissions(String roleCode, List<RolePermissionRule> permissions, LocalDateTime now) {
        var saved = permissions.stream()
                .map(permission -> repository.save(new RolePermissionEntity(
                        "PERM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                        roleCode,
                        permission.httpMethod().toUpperCase(),
                        permission.pathPattern(),
                        permission.enabled(),
                        permission.description(),
                        permission.sortOrder(),
                        now
                )))
                .map(this::toRule)
                .sorted((left, right) -> Integer.compare(left.sortOrder(), right.sortOrder()))
                .toList();
        return new RolePermissionConfig(roleCode, saved);
    }

    private List<RolePermissionConfig> defaultPermissionConfigs() {
        return List.of(
                new RolePermissionConfig("RND_ASSISTANT", List.of(
                        rule("POST", "/api/v1/sample-requests", "创建样品需求", 10),
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 20),
                        rule("GET", "/api/v1/sample-requests/*", "查看样品需求详情", 25),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 26),
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 30),
                        rule("POST", "/api/v1/sample-versions/*/shipments", "登记寄样", 40),
                        rule("GET", "/api/v1/shipments", "查看寄样列表", 45),
                        rule("GET", "/api/v1/shipments/*/detail", "查看寄样详情", 50),
                        rule("GET", "/api/v1/rnd-tasks", "查看待寄样任务", 48),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看任务详情", 49),
                        rule("POST", "/api/v1/experiment-forms/*/submit-test", "通知内部测试", 49),
                        rule("POST", "/api/v1/shipments/*/feedback", "登记客户反馈", 60),
                        rule("POST", "/api/v1/sample-versions/*/pricing-files", "生成核价文件", 70),
                        rule("GET", "/api/v1/pricing-files", "查看核价文件列表", 80),
                        rule("GET", "/api/v1/pricing-files/*/detail", "查看核价文件详情", 90),
                        rule("GET", "/api/v1/pricing-files/*/download", "下载核价文件", 100),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 110),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 120),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 130),
                        rule("POST", "/api/v1/demo/seed-feedback", "初始化寄样反馈演示数据", 200)
                )),
                new RolePermissionConfig("RND_DIRECTOR", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("GET", "/api/v1/sample-requests/*", "查看样品需求详情", 15),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 16),
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 20),
                        rule("POST", "/api/v1/sample-requests/*/approve", "审核样品需求", 30),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 40),
                        rule("GET", "/api/v1/rnd-tasks", "查看研发任务列表", 50),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看研发任务详情", 60),
                        rule("POST", "/api/v1/rnd-tasks/*/assign", "分发研发任务", 70),
                        rule("POST", "/api/v1/rnd-tasks/*/accept", "接受研发任务", 75),
                        rule("GET", "/api/v1/sample-versions/*/process-steps", "查看工序步骤", 76),
                        rule("POST", "/api/v1/rnd-tasks/*/experiment-form/draft", "保存实验单草稿", 77),
                        rule("POST", "/api/v1/experiment-forms/*/submit-test", "提交内部测试", 78),
                        rule("POST", "/api/v1/experiment-forms/*/attachments", "上传实验附件", 79),
                        rule("GET", "/api/v1/pricing-files", "查看核价文件列表", 80),
                        rule("GET", "/api/v1/shipments/*/detail", "查看寄样详情", 90),
                        rule("GET", "/api/v1/pricing-files/*/detail", "查看核价文件详情", 100),
                        rule("GET", "/api/v1/pricing-files/*/download", "下载核价文件", 110),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 120),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 130),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 140)
                )),
                new RolePermissionConfig("RND_ENGINEER", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 12),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 15),
                        rule("GET", "/api/v1/sample-versions/*/process-steps", "查看工序步骤", 16),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 20),
                        rule("GET", "/api/v1/rnd-tasks", "查看研发任务列表", 30),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看研发任务详情", 40),
                        rule("POST", "/api/v1/rnd-tasks/*/accept", "接受研发任务", 50),
                        rule("POST", "/api/v1/rnd-tasks/*/experiment-form/draft", "保存实验单草稿", 60),
                        rule("POST", "/api/v1/experiment-forms/*/submit-test", "提交内部测试", 70),
                        rule("POST", "/api/v1/experiment-forms/*/attachments", "上传实验附件", 80),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 90),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 100)
                )),
                new RolePermissionConfig("TESTER", List.of(
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 3),
                        rule("GET", "/api/v1/rnd-tasks", "查看研发任务列表", 8),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 5),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看研发任务详情", 10),
                        rule("POST", "/api/v1/test-assignments/*/pass", "提交测试通过", 20),
                        rule("POST", "/api/v1/test-assignments/*/fail-resample", "提交测试不通过复打样", 30),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 40),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 50)
                )),
                new RolePermissionConfig("QA_TESTER", List.of(
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 3),
                        rule("GET", "/api/v1/rnd-tasks", "查看研发任务列表", 8),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 5),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看研发任务详情", 10),
                        rule("POST", "/api/v1/test-assignments/*/pass", "提交测试通过", 20),
                        rule("POST", "/api/v1/test-assignments/*/fail-resample", "提交测试不通过复打样", 30),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 40),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 50)
                )),
                new RolePermissionConfig("FINANCE", List.of(
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 5),
                        rule("GET", "/api/v1/pricing-files", "查看核价文件列表", 8),
                        rule("GET", "/api/v1/pricing-files/*/detail", "查看核价文件详情", 10),
                        rule("GET", "/api/v1/pricing-files/*/download", "下载核价文件", 20),
                        rule("POST", "/api/v1/pricing-files/*/notify-finance", "处理核价通知", 30),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 40),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 50)
                )),
                new RolePermissionConfig("MANAGER", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("GET", "/api/v1/sample-requests/*", "查看样品需求详情", 15),
                        rule("GET", "/api/v1/sample-projects/*/version-timeline", "查看版本时间线", 16),
                        rule("GET", "/api/v1/dashboard/overview", "查看工作台汇总", 20),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 30),
                        rule("GET", "/api/v1/rnd-tasks", "查看研发任务列表", 40),
                        rule("GET", "/api/v1/rnd-tasks/*/detail", "查看研发任务详情", 50),
                        rule("GET", "/api/v1/pricing-files", "查看核价文件列表", 60),
                        rule("GET", "/api/v1/shipments/*/detail", "查看寄样详情", 70),
                        rule("GET", "/api/v1/pricing-files/*/detail", "查看核价文件详情", 80),
                        rule("GET", "/api/v1/pricing-files/*/download", "下载核价文件", 90),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 100),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 110),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 120)
                ))
        );
    }

    private RolePermissionRule rule(String httpMethod, String pathPattern, String description, int sortOrder) {
        return new RolePermissionRule(null, httpMethod, pathPattern, true, description, sortOrder);
    }

    public boolean hasPermission(String role, String method, String uri) {
        if (role == null || role.isBlank()) {
            return false;
        }
        if (hasAnyRole(role, "ADMIN", "SYSTEM_ADMIN")) {
            return true;
        }
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        if (repository.countByRoleCode(role) > 0) {
            return repository.findByRoleCodeAndEnabledTrueOrderBySortOrderAsc(role).stream()
                    .anyMatch(permission -> permission.getHttpMethod().equalsIgnoreCase(normalizedMethod)
                            && pathMatcher.match(permission.getPathPattern(), uri));
        }
        return hasDefaultPermission(role, normalizedMethod, uri);
    }

    private RolePermissionRule toRule(RolePermissionEntity entity) {
        return new RolePermissionRule(
                entity.getId(),
                entity.getHttpMethod(),
                entity.getPathPattern(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getSortOrder()
        );
    }

    private boolean hasDefaultPermission(String role, String method, String uri) {
        if (isArchiveRead(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER");
        }
        if (isStoppedProjectRead(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "MANAGER");
        }
        if (isDashboardRead(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER");
        }
        if (isTaskList(method, uri)) {
            return hasAnyRole(role, "RND_DIRECTOR", "RND_ENGINEER", "RND_ASSISTANT", "TESTER", "QA_TESTER", "MANAGER");
        }
        if (isTaskDetail(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER");
        }
        if (isShipmentDetail(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "MANAGER");
        }
        if (isShipmentList(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "MANAGER");
        }
        if (isPricingList(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "FINANCE", "MANAGER");
        }
        if (isPricingDetail(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "FINANCE", "MANAGER");
        }
        if (isPricingDownload(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "FINANCE", "MANAGER");
        }
        if (isVersionTimelineRead(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER");
        }
        if (isProcessStepsRead(method, uri)) {
            return hasAnyRole(role, "RND_ENGINEER", "RND_DIRECTOR", "RND_ASSISTANT", "MANAGER");
        }
        if (isSampleRequestDetail(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "MANAGER");
        }
        return switch (role) {
            case "RND_ASSISTANT" -> isSampleRequestCreate(method, uri)
                    || isSampleRequestList(method, uri)
                    || isTaskList(method, uri)
                    || isTaskDetail(method, uri)
                    || isSubmitExperimentForTest(method, uri)
                    || isShipmentWrite(method, uri)
                    || isCustomerFeedbackWrite(method, uri)
                    || isPricingWrite(method, uri)
                    || isFeishuOperation(method, uri);
            case "RND_DIRECTOR" -> isSampleRequestList(method, uri)
                    || isSampleRequestApprove(method, uri)
                    || isTaskPool(method, uri)
                    || isTaskAssign(method, uri)
                    || isTaskAccept(method, uri)
                    || isExperimentWrite(method, uri)
                    || isFeishuOperation(method, uri);
            case "RND_ENGINEER" -> isTaskPool(method, uri)
                    || isSampleRequestList(method, uri)
                    || isTaskAccept(method, uri)
                    || isExperimentWrite(method, uri)
                    || isFeishuOperation(method, uri);
            case "TESTER", "QA_TESTER" -> isTestWrite(method, uri);
            case "FINANCE" -> isFinanceWrite(method, uri);
            case "MANAGER" -> isSampleRequestList(method, uri) || isTaskPool(method, uri) || isFeishuOperation(method, uri);
            default -> false;
        };
    }

    private boolean hasAnyRole(String role, String... allowedRoles) {
        for (String allowedRole : allowedRoles) {
            if (allowedRole.equals(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSampleRequestCreate(String method, String uri) {
        return "POST".equals(method) && uri.equals("/api/v1/sample-requests");
    }

    private boolean isSampleRequestList(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/sample-requests");
    }

    private boolean isSampleRequestDetail(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/sample-requests/[^/]+$");
    }

    private boolean isVersionTimelineRead(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/sample-projects/[^/]+/version-timeline$");
    }

    private boolean isProcessStepsRead(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/sample-versions/[^/]+/process-steps$");
    }

    private boolean isSampleRequestApprove(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/sample-requests/[^/]+/approve$");
    }

    private boolean isTaskPool(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/rnd-tasks/pool");
    }

    private boolean isTaskList(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/rnd-tasks");
    }

    private boolean isTaskDetail(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/rnd-tasks/[^/]+/detail$");
    }

    private boolean isTaskAssign(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/rnd-tasks/[^/]+/assign$");
    }

    private boolean isTaskAccept(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/rnd-tasks/[^/]+/accept$");
    }

    private boolean isExperimentWrite(String method, String uri) {
        return "POST".equals(method)
                && (uri.matches("^/api/v1/rnd-tasks/[^/]+/experiment-form/draft$")
                || isSubmitExperimentForTest(method, uri)
                || uri.matches("^/api/v1/experiment-forms/[^/]+/attachments$"));
    }

    private boolean isSubmitExperimentForTest(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/experiment-forms/[^/]+/submit-test$");
    }

    private boolean isTestWrite(String method, String uri) {
        return "POST".equals(method)
                && (uri.matches("^/api/v1/test-assignments/[^/]+/pass$")
                || uri.matches("^/api/v1/test-assignments/[^/]+/fail-resample$"));
    }

    private boolean isShipmentWrite(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/sample-versions/[^/]+/shipments$");
    }

    private boolean isCustomerFeedbackWrite(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/shipments/[^/]+/feedback$");
    }

    private boolean isPricingWrite(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/sample-versions/[^/]+/pricing-files$");
    }

    private boolean isPricingList(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/pricing-files");
    }

    private boolean isShipmentDetail(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/shipments/[^/]+/detail$");
    }

    private boolean isShipmentList(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/shipments");
    }

    private boolean isPricingDetail(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/pricing-files/[^/]+/detail$");
    }

    private boolean isPricingDownload(String method, String uri) {
        return "GET".equals(method) && uri.matches("^/api/v1/pricing-files/[^/]+/download$");
    }

    private boolean isFinanceWrite(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/pricing-files/[^/]+/notify-finance$");
    }

    private boolean isArchiveRead(String method, String uri) {
        return "GET".equals(method)
                && (uri.matches("^/api/v1/sample-versions/[^/]+/archive-files$")
                || uri.matches("^/api/v1/archive-files/[^/]+/download$"));
    }

    private boolean isStoppedProjectRead(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/sample-projects/stopped");
    }

    private boolean isDashboardRead(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/dashboard/overview");
    }

    private boolean isFeishuOperation(String method, String uri) {
        return uri.equals("/api/v1/feishu/integration/status")
                || uri.equals("/api/v1/feishu/notifications/pending")
                || ("POST".equals(method) && uri.equals("/api/v1/feishu/notifications/dispatch"));
    }
}
