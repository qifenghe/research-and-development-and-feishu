package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
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

    public FeishuLoginResult oauthCallback(OauthCallbackCommand command) {
        var feishuUserId = resolveFeishuUserId(command.code());
        var user = userAccountRepository.findByFeishuUserId(feishuUserId)
                .filter(existing -> "ACTIVE".equals(existing.toModel().status()))
                .orElseThrow(() -> new BusinessException("FEISHU_USER_NOT_BOUND", "飞书用户未绑定系统账号"));
        return new FeishuLoginResult(feishuUserId, user.toModel(), "mock-token-" + feishuUserId);
    }

    @Transactional
    public FeishuDispatchResult dispatchPendingNotifications() {
        var pending = feishuNotificationRepository.findByStatusOrderByCreatedAtAsc("PENDING_SEND");
        var sentCount = 0;
        var failedCount = 0;
        for (var notification : pending) {
            var sendResult = sendNotification(notification);
            if (sendResult.success()) {
                notification.markSent(now());
                sentCount++;
            } else {
                notification.markFailed(sendResult.errorMessage());
                failedCount++;
            }
            feishuNotificationRepository.save(notification);
        }
        return new FeishuDispatchResult(pending.size(), sentCount, failedCount);
    }

    private FeishuSendResult sendNotification(FeishuNotificationEntity notification) {
        if (notification.getRecipientFeishuUserId().startsWith("fail_")) {
            return FeishuSendResult.failed("模拟飞书发送失败");
        }
        return FeishuSendResult.sent();
    }

    private String resolveFeishuUserId(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("FEISHU_OAUTH_CODE_REQUIRED", "飞书免登 code 不能为空");
        }
        if (code.startsWith("mock:")) {
            var feishuUserId = code.substring("mock:".length());
            if (!feishuUserId.isBlank()) {
                return feishuUserId;
            }
        }
        throw new BusinessException("FEISHU_OAUTH_CODE_UNSUPPORTED", "当前仅支持本地 mock 飞书免登 code");
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

    public record OauthCallbackCommand(String code) {
    }

    private record FeishuSendResult(boolean success, String errorMessage) {
        private static FeishuSendResult sent() {
            return new FeishuSendResult(true, null);
        }

        private static FeishuSendResult failed(String errorMessage) {
            return new FeishuSendResult(false, errorMessage);
        }
    }
}
