package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.persistence.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthService {
    private final Clock clock;
    private final UserAccountRepository userAccountRepository;
    private final PasswordHashService passwordHashService;
    private final SessionTokenService sessionTokenService;
    private final DefaultWebAccountBootstrapService defaultWebAccountBootstrapService;

    @Autowired
    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService,
            SessionTokenService sessionTokenService,
            DefaultWebAccountBootstrapService defaultWebAccountBootstrapService
    ) {
        this(Clock.systemDefaultZone(), userAccountRepository, passwordHashService, sessionTokenService, defaultWebAccountBootstrapService);
    }

    AuthService(
            Clock clock,
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService,
            SessionTokenService sessionTokenService,
            DefaultWebAccountBootstrapService defaultWebAccountBootstrapService
    ) {
        this.clock = clock;
        this.userAccountRepository = userAccountRepository;
        this.passwordHashService = passwordHashService;
        this.sessionTokenService = sessionTokenService;
        this.defaultWebAccountBootstrapService = defaultWebAccountBootstrapService;
    }

    @Transactional
    public AuthLoginResult login(String username, String password) {
        var normalizedUsername = username == null ? "" : username.trim();
        var user = userAccountRepository.findByUsername(normalizedUsername)
                .orElseGet(() -> {
                    defaultWebAccountBootstrapService.seedDefaultAccounts();
                    return userAccountRepository.findByUsername(normalizedUsername)
                            .orElseThrow(() -> invalidCredentials());
                });
        if (!"ACTIVE".equals(user.toModel().status())) {
            throw new BusinessException("AUTH_USER_DISABLED", "账号已停用");
        }
        if (!passwordHashService.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        user.markLoggedIn(LocalDateTime.now(clock));
        var saved = userAccountRepository.save(user).toModel();
        return new AuthLoginResult(saved, sessionTokenService.issue(saved));
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("AUTH_CREDENTIAL_INVALID", "账号或密码不正确");
    }
}
