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
import java.util.UUID;

@Service
public class UserSettingsService {
    private final Clock clock;
    private final UserAccountRepository userAccountRepository;

    @Autowired
    public UserSettingsService(UserAccountRepository userAccountRepository) {
        this(Clock.systemDefaultZone(), userAccountRepository);
    }

    UserSettingsService(Clock clock, UserAccountRepository userAccountRepository) {
        this.clock = clock;
        this.userAccountRepository = userAccountRepository;
    }

    public List<UserAccount> users() {
        return userAccountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(UserAccountEntity::toModel)
                .toList();
    }

    @Transactional
    public UserAccount saveUser(SaveUserCommand command) {
        var now = now();
        var user = userAccountRepository.findByFeishuUserId(command.feishuUserId())
                .map(existing -> {
                    existing.update(command.name(), command.role(), command.departmentName(), now);
                    return existing;
                })
                .orElseGet(() -> new UserAccountEntity(
                        nextId(),
                        command.name(),
                        command.feishuUserId(),
                        command.role(),
                        command.departmentName(),
                        "ACTIVE",
                        now,
                        now
                ));
        return userAccountRepository.save(user).toModel();
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
        user.updateStatus(status, now());
        return userAccountRepository.save(user).toModel();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextId() {
        return "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    public record SaveUserCommand(
            String feishuUserId,
            String name,
            String role,
            String departmentName
    ) {
    }
}
