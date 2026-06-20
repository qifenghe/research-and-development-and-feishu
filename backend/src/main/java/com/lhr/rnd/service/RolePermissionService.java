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
                        rule("POST", "/api/v1/sample-versions/*/shipments", "登记寄样", 30),
                        rule("POST", "/api/v1/shipments/*/feedback", "登记客户反馈", 40),
                        rule("POST", "/api/v1/sample-versions/*/pricing-files", "生成核价文件", 50),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 60),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 70),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 80)
                )),
                new RolePermissionConfig("RND_DIRECTOR", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("POST", "/api/v1/sample-requests/*/approve", "审核样品需求", 20),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 30),
                        rule("POST", "/api/v1/rnd-tasks/*/assign", "分发研发任务", 40),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 50),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 60),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 70)
                )),
                new RolePermissionConfig("RND_ENGINEER", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 20),
                        rule("POST", "/api/v1/rnd-tasks/*/accept", "接受研发任务", 30),
                        rule("POST", "/api/v1/rnd-tasks/*/experiment-form/draft", "保存实验单草稿", 40),
                        rule("POST", "/api/v1/experiment-forms/*/submit-test", "提交内部测试", 50),
                        rule("POST", "/api/v1/experiment-forms/*/attachments", "上传实验附件", 60),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 70),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 80)
                )),
                new RolePermissionConfig("TESTER", List.of(
                        rule("POST", "/api/v1/test-assignments/*/pass", "提交测试通过", 10),
                        rule("POST", "/api/v1/test-assignments/*/fail-resample", "提交测试不通过复打样", 20),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 30),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 40)
                )),
                new RolePermissionConfig("QA_TESTER", List.of(
                        rule("POST", "/api/v1/test-assignments/*/pass", "提交测试通过", 10),
                        rule("POST", "/api/v1/test-assignments/*/fail-resample", "提交测试不通过复打样", 20),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 30),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 40)
                )),
                new RolePermissionConfig("FINANCE", List.of(
                        rule("POST", "/api/v1/pricing-files/*/notify-finance", "处理核价通知", 10),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 20),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 30)
                )),
                new RolePermissionConfig("MANAGER", List.of(
                        rule("GET", "/api/v1/sample-requests", "查看样品需求列表", 10),
                        rule("GET", "/api/v1/rnd-tasks/pool", "查看研发任务池", 20),
                        rule("GET", "/api/v1/sample-projects/stopped", "查看停止/废弃项目池", 30),
                        rule("GET", "/api/v1/sample-versions/*/archive-files", "查看归档文件", 40),
                        rule("GET", "/api/v1/archive-files/*/download", "下载归档文件", 50)
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
        return switch (role) {
            case "RND_ASSISTANT" -> isSampleRequestCreate(method, uri)
                    || isSampleRequestList(method, uri)
                    || isShipmentWrite(method, uri)
                    || isCustomerFeedbackWrite(method, uri)
                    || isPricingWrite(method, uri)
                    || isFeishuOperation(method, uri);
            case "RND_DIRECTOR" -> isSampleRequestList(method, uri)
                    || isSampleRequestApprove(method, uri)
                    || isTaskPool(method, uri)
                    || isTaskAssign(method, uri)
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

    private boolean isSampleRequestApprove(String method, String uri) {
        return "POST".equals(method) && uri.matches("^/api/v1/sample-requests/[^/]+/approve$");
    }

    private boolean isTaskPool(String method, String uri) {
        return "GET".equals(method) && uri.equals("/api/v1/rnd-tasks/pool");
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
                || uri.matches("^/api/v1/experiment-forms/[^/]+/submit-test$")
                || uri.matches("^/api/v1/experiment-forms/[^/]+/attachments$"));
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

    private boolean isFeishuOperation(String method, String uri) {
        return uri.equals("/api/v1/feishu/integration/status")
                || uri.equals("/api/v1/feishu/notifications/pending")
                || ("POST".equals(method) && uri.equals("/api/v1/feishu/notifications/dispatch"));
    }
}
