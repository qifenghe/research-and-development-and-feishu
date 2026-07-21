package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.persistence.entity.UserAccountEntity;
import com.lhr.rnd.persistence.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSettingsService {
    private static final String SUPER_ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final Clock clock;
    private final UserAccountRepository userAccountRepository;
    private final PasswordHashService passwordHashService;
    private final RoleDefinitionService roleDefinitionService;

    @Autowired
    public UserSettingsService(
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService,
            RoleDefinitionService roleDefinitionService
    ) {
        this(Clock.systemDefaultZone(), userAccountRepository, passwordHashService, roleDefinitionService);
    }

    UserSettingsService(Clock clock, UserAccountRepository userAccountRepository, PasswordHashService passwordHashService) {
        this(clock, userAccountRepository, passwordHashService, null);
    }

    UserSettingsService(
            Clock clock,
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService,
            RoleDefinitionService roleDefinitionService
    ) {
        this.clock = clock;
        this.userAccountRepository = userAccountRepository;
        this.passwordHashService = passwordHashService;
        this.roleDefinitionService = roleDefinitionService;
    }

    public List<UserAccount> users() {
        return userAccountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(UserAccountEntity::toModel)
                .toList();
    }

    public List<UserAccount> activeRndAssignees() {
        return userAccountRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(user -> ACTIVE_STATUS.equals(user.getStatus()))
                .filter(user -> "RND_DIRECTOR".equals(user.getRole()) || "RND_ENGINEER".equals(user.getRole()))
                .map(UserAccountEntity::toModel)
                .toList();
    }

    @Transactional
    public UserAccount saveUser(SaveUserCommand command) {
        if (roleDefinitionService != null) {
            roleDefinitionService.assertAssignable(command.role());
        }
        var now = now();
        var username = command.username() == null || command.username().isBlank()
                ? command.idOrFeishuUserId().trim()
                : command.username().trim();
        if (username.isBlank()) {
            throw new BusinessException("USER_USERNAME_REQUIRED", "账号不能为空");
        }
        assertUsernameAvailable(username, command.idOrFeishuUserId());
        var resolvedFeishuUserId = blankToNull(command.feishuUserId());
        if (resolvedFeishuUserId == null && !command.idOrFeishuUserId().startsWith("USR-") && !command.idOrFeishuUserId().startsWith("new-")) {
            resolvedFeishuUserId = command.idOrFeishuUserId().trim();
        }
        final var feishuUserId = resolvedFeishuUserId;
        var user = findExistingUser(command.idOrFeishuUserId(), username, feishuUserId)
                .map(existing -> updateExisting(command, username, feishuUserId, existing, now))
                .orElseGet(() -> new UserAccountEntity(
                        nextId(),
                        username,
                        passwordHashService.hash(defaultPassword(command.password())),
                        command.name(),
                        feishuUserId,
                        command.role(),
                        command.departmentName(),
                        "ACTIVE",
                        null,
                        now,
                        now
                ));
        if (command.password() != null && !command.password().isBlank()) {
            user.updatePasswordHash(passwordHashService.hash(command.password()), now);
        } else if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.updatePasswordHash(passwordHashService.hash("123456"), now);
        }
        return userAccountRepository.save(user).toModel();
    }

    private UserAccountEntity updateExisting(
            SaveUserCommand command,
            String username,
            String feishuUserId,
            UserAccountEntity existing,
            LocalDateTime now
    ) {
        assertNotRemovingLastSuperAdmin(existing, command.role(), ACTIVE_STATUS);
        existing.updateAccount(
                username,
                command.name(),
                feishuUserId == null ? existing.getFeishuUserId() : feishuUserId,
                command.role(),
                command.departmentName(),
                now
        );
        return existing;
    }

    private Optional<UserAccountEntity> findExistingUser(String idOrFeishuUserId, String username, String feishuUserId) {
        return userAccountRepository.findById(idOrFeishuUserId)
                .or(() -> userAccountRepository.findByUsername(username))
                .or(() -> feishuUserId == null ? Optional.empty() : userAccountRepository.findByFeishuUserId(feishuUserId))
                .or(() -> userAccountRepository.findByFeishuUserId(idOrFeishuUserId));
    }

    private void assertUsernameAvailable(String username, String currentIdOrFeishuUserId) {
        userAccountRepository.findByUsername(username).ifPresent(existing -> {
            if (!existing.getId().equals(currentIdOrFeishuUserId)
                    && !username.equals(currentIdOrFeishuUserId)
                    && (existing.getFeishuUserId() == null || !existing.getFeishuUserId().equals(currentIdOrFeishuUserId))) {
                throw new BusinessException("USER_USERNAME_DUPLICATED", "账号已存在");
            }
        });
    }

    private String defaultPassword(String password) {
        return password == null || password.isBlank() ? "123456" : password;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public UserAccount enableUser(String id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public UserAccount disableUser(String id) {
        return updateStatus(id, "INACTIVE");
    }

    private UserAccount updateStatus(String id, String status) {
        var user = userAccountRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_ACCOUNT_NOT_FOUND", "用户不存在"));
        assertNotRemovingLastSuperAdmin(user, user.getRole(), status);
        user.updateStatus(status, now());
        return userAccountRepository.save(user).toModel();
    }

    private void assertNotRemovingLastSuperAdmin(
            UserAccountEntity user,
            String nextRole,
            String nextStatus
    ) {
        if (!SUPER_ADMIN_ROLE.equals(user.getRole()) || !ACTIVE_STATUS.equals(user.getStatus())) {
            return;
        }
        if (SUPER_ADMIN_ROLE.equals(nextRole) && ACTIVE_STATUS.equals(nextStatus)) {
            return;
        }
        if (userAccountRepository.countByRoleAndStatus(SUPER_ADMIN_ROLE, ACTIVE_STATUS) <= 1) {
            throw new BusinessException("LAST_SYSTEM_ADMIN_REQUIRED", "系统至少需要保留一个启用中的超级管理员");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextId() {
        return "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    public record SaveUserCommand(
            String idOrFeishuUserId,
            String username,
            String name,
            String feishuUserId,
            String role,
            String departmentName,
            String password
    ) {
    }
}
