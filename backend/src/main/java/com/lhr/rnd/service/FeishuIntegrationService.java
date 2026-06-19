package com.lhr.rnd.service;

import com.lhr.rnd.model.FeishuNotification;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;
import com.lhr.rnd.persistence.entity.UserAccountEntity;
import com.lhr.rnd.persistence.repository.FeishuNotificationRepository;
import com.lhr.rnd.persistence.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FeishuIntegrationService {
    private final Clock clock;
    private final UserAccountRepository userAccountRepository;
    private final FeishuNotificationRepository feishuNotificationRepository;

    @Autowired
    public FeishuIntegrationService(
            UserAccountRepository userAccountRepository,
            FeishuNotificationRepository feishuNotificationRepository
    ) {
        this(Clock.systemDefaultZone(), userAccountRepository, feishuNotificationRepository);
    }

    FeishuIntegrationService(
            Clock clock,
            UserAccountRepository userAccountRepository,
            FeishuNotificationRepository feishuNotificationRepository
    ) {
        this.clock = clock;
        this.userAccountRepository = userAccountRepository;
        this.feishuNotificationRepository = feishuNotificationRepository;
    }

    @Transactional
    public UserAccount bindUser(BindFeishuUserCommand command) {
        var now = now();
        var user = userAccountRepository.findByFeishuUserId(command.feishuUserId())
                .map(existing -> {
                    existing.update(command.name(), command.role(), command.departmentName(), now);
                    return existing;
                })
                .orElseGet(() -> new UserAccountEntity(
                        nextId("USR"),
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
    public void createTaskAssignedNotification(RndTask task) {
        if (task.assigneeName() == null || task.assigneeName().isBlank()) {
            return;
        }
        userAccountRepository.findFirstByNameAndStatus(task.assigneeName(), "ACTIVE")
                .ifPresent(user -> feishuNotificationRepository.save(new FeishuNotificationEntity(
                        nextId("FSN"),
                        "RND_TASK",
                        task.id(),
                        user.getId(),
                        user.getFeishuUserId(),
                        "RND_TASK_ASSIGNED",
                        "研发任务分发通知",
                        "%s %s 已分发给你，请在飞书自建应用中接受任务。".formatted(task.productName(), task.versionCode()),
                        "PENDING_SEND",
                        now(),
                        null
                )));
    }

    public List<FeishuNotification> pendingNotifications() {
        return feishuNotificationRepository.findByStatusOrderByCreatedAtAsc("PENDING_SEND").stream()
                .map(FeishuNotificationEntity::toModel)
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    public record BindFeishuUserCommand(
            String name,
            String feishuUserId,
            String role,
            String departmentName
    ) {
    }
}
