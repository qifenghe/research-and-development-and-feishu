package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.service.RolePermissionService;
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
    public static final String SESSION_PRINCIPAL_ATTRIBUTE = "sessionPrincipal";
    private static final String SESSION_ATTRIBUTE = SESSION_PRINCIPAL_ATTRIBUTE;
    private final SessionProperties sessionProperties;
    private final SessionTokenService sessionTokenService;
    private final RolePermissionService rolePermissionService;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationInterceptor(
            SessionProperties sessionProperties,
            SessionTokenService sessionTokenService,
            RolePermissionService rolePermissionService,
            ObjectMapper objectMapper
    ) {
        this.sessionProperties = sessionProperties;
        this.sessionTokenService = sessionTokenService;
        this.rolePermissionService = rolePermissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isPublicPath(request.getRequestURI())) {
            return true;
        }
        if (!sessionProperties.isAuthRequired() && sessionProperties.isTestBusinessApiAuthenticationBypass()) {
            return true;
        }
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "SESSION_TOKEN_REQUIRED", "请先登录");
            return false;
        }
        try {
            SessionPrincipal principal = sessionTokenService.verify(authorization.substring("Bearer ".length()).trim());
            if (!rolePermissionService.hasPermission(principal.role(), request.getMethod(), request.getRequestURI())) {
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
                || uri.equals("/api/v1/auth/login")
                || uri.equals("/api/v1/auth/logout")
                || uri.equals("/api/v1/session/me")
                || uri.equals("/api/v1/feishu/integration/status")
                || uri.equals("/api/v1/feishu/users/bind")
                || uri.equals("/api/v1/feishu/oauth/callback")
                || uri.equals("/api/v1/demo/seed-users")
                || uri.equals("/api/v1/feishu/card-actions")
                || uri.equals("/api/v1/feishu/events");
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
