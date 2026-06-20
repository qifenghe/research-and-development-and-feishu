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
    private final FeishuProperties feishuProperties;
    private final SessionTokenService sessionTokenService;
    private final UserAccountRepository userAccountRepository;
    private final FeishuNotificationRepository feishuNotificationRepository;
    private final FeishuIdentityClientProvider feishuIdentityClientProvider;

    @Autowired
    public FeishuIntegrationService(
            UserAccountRepository userAccountRepository,
            FeishuNotificationRepository feishuNotificationRepository,
            FeishuIdentityClientProvider feishuIdentityClientProvider,
            FeishuProperties feishuProperties,
            SessionTokenService sessionTokenService
    ) {
        this(
                Clock.systemDefaultZone(),
                userAccountRepository,
                feishuNotificationRepository,
                feishuIdentityClientProvider,
                feishuProperties,
                sessionTokenService
        );
    }

    FeishuIntegrationService(
            Clock clock,
            UserAccountRepository userAccountRepository,
            FeishuNotificationRepository feishuNotificationRepository,
            FeishuIdentityClientProvider feishuIdentityClientProvider,
            FeishuProperties feishuProperties,
            SessionTokenService sessionTokenService
    ) {
        this.clock = clock;
        this.feishuProperties = feishuProperties;
        this.sessionTokenService = sessionTokenService;
        this.userAccountRepository = userAccountRepository;
        this.feishuNotificationRepository = feishuNotificationRepository;
        this.feishuIdentityClientProvider = feishuIdentityClientProvider;
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
                        taskAssignedContent(task),
                        "PENDING_SEND",
                        now(),
                        null
                )));
    }

    private String taskAssignedContent(RndTask task) {
        var dueDate = task.dueDate() == null ? "未设置" : task.dueDate().toString();
        return """
                **产品**：%s
                **版本**：%s
                **任务编号**：%s
                **负责人**：%s
                **截止日期**：%s

                请在飞书自建应用中接受任务，并进入现场打样实验单。
                """.formatted(
                task.productName(),
                task.versionCode(),
                task.id(),
                task.assigneeName(),
                dueDate
        ).trim();
    }

    public List<FeishuNotification> pendingNotifications() {
        return feishuNotificationRepository.findByStatusOrderByCreatedAtAsc("PENDING_SEND").stream()
                .map(FeishuNotificationEntity::toModel)
                .toList();
    }

    public FeishuLoginResult oauthCallback(OauthCallbackCommand command) {
        var feishuUserId = feishuIdentityClientProvider.current().exchangeCodeForFeishuUserId(command.code());
        var user = userAccountRepository.findByFeishuUserId(feishuUserId)
                .filter(existing -> "ACTIVE".equals(existing.toModel().status()))
                .orElseThrow(() -> new BusinessException("FEISHU_USER_NOT_BOUND", "飞书用户未绑定系统账号"));
        return new FeishuLoginResult(feishuUserId, user.toModel(), sessionTokenService.issue(user.toModel()));
    }

    public UserAccount activeUserByFeishuUserId(String feishuUserId) {
        return userAccountRepository.findByFeishuUserId(feishuUserId)
                .map(UserAccountEntity::toModel)
                .filter(user -> "ACTIVE".equals(user.status()))
                .orElseThrow(() -> new BusinessException("FEISHU_USER_NOT_BOUND", "飞书用户未绑定系统账号"));
    }

    public void verifyCardActionSecret(String providedSecret) {
        if (!feishuProperties.cardActionSecretConfigured()) {
            return;
        }
        if (!feishuProperties.getCardActionSecret().equals(providedSecret)) {
            throw new BusinessException("FEISHU_CARD_ACTION_UNAUTHORIZED", "飞书卡片回调密钥不正确");
        }
    }

    public FeishuIntegrationStatus integrationStatus() {
        return feishuIdentityClientProvider.status();
    }

    @Transactional
    public FeishuDispatchResult dispatchPendingNotifications() {
        var pending = feishuNotificationRepository.findByStatusOrderByCreatedAtAsc("PENDING_SEND");
        var sentCount = 0;
        var failedCount = 0;
        for (var notification : pending) {
            var sendResult = feishuIdentityClientProvider.current().sendNotification(notification);
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

}
