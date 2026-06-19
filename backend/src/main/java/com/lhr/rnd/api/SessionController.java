package com.lhr.rnd.api;

import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.SessionTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    private final SessionTokenService sessionTokenService;

    public SessionController(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/me")
    public ApiResponse<SessionPrincipal> currentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(sessionTokenService.verify(bearerToken(authorization)));
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("SESSION_TOKEN_REQUIRED", "请先登录");
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
