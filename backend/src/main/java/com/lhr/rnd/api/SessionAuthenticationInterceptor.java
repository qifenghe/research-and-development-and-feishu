package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.service.SessionProperties;
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
            request.setAttribute(SESSION_ATTRIBUTE, sessionTokenService.verify(authorization.substring("Bearer ".length()).trim()));
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

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(code, message)));
    }
}
