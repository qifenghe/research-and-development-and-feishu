package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.service.SessionProperties;
import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionAuthenticationInterceptor implements HandlerInterceptor {
    private static final String SESSION_ATTRIBUTE = "sessionPrincipal";
    private final SessionProperties sessionProperties;
    private final SessionTokenService sessionTokenService;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationInterceptor(
            SessionProperties sessionProperties,
            SessionTokenService sessionTokenService,
            ObjectMapper objectMapper
    ) {
        this.sessionProperties = sessionProperties;
        this.sessionTokenService = sessionTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!sessionProperties.isAuthRequired() || isPublicPath(request.getRequestURI())) {
            return true;
        }
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "SESSION_TOKEN_REQUIRED", "请先登录");
            return false;
        }
        try {
            SessionPrincipal principal = sessionTokenService.verify(authorization.substring("Bearer ".length()).trim());
            if (!hasPermission(principal.role(), request.getMethod(), request.getRequestURI())) {
                writeForbidden(response);
                return false;
            }
            request.setAttribute(SESSION_ATTRIBUTE, principal);
            return true;
        } catch (BusinessException exception) {
            writeUnauthorized(response, exception.code(), exception.getMessage());
            return false;
        }
    }

    private boolean isPublicPath(String uri) {
        return uri.startsWith("/actuator")
                || uri.equals("/api/v1/session/me")
                || uri.equals("/api/v1/feishu/users/bind")
                || uri.equals("/api/v1/feishu/oauth/callback")
                || uri.equals("/api/v1/feishu/card-actions")
                || uri.equals("/api/v1/feishu/events");
    }

    private boolean hasPermission(String role, String method, String uri) {
        if (role == null || role.isBlank()) {
            return false;
        }
        if (hasAnyRole(role, "ADMIN", "SYSTEM_ADMIN")) {
            return true;
        }
        if (isArchiveRead(method, uri)) {
            return hasAnyRole(role, "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER");
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

    private boolean isFeishuOperation(String method, String uri) {
        return uri.equals("/api/v1/feishu/integration/status")
                || uri.equals("/api/v1/feishu/notifications/pending")
                || ("POST".equals(method) && uri.equals("/api/v1/feishu/notifications/dispatch"));
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(code, message)));
    }

    private void writeForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure("SESSION_ROLE_FORBIDDEN", "当前角色无权访问该功能")));
    }
}
