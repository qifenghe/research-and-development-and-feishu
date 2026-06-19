package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiFeishuIdentityClientTest {

    @Test
    void exchangesCodeThroughTenantTokenAndOauthUserFetcher() {
        var properties = openApiProperties();
        var tokenFetcher = new FixedTokenFetcher();
        var tokenService = new FeishuTenantAccessTokenService(
                properties,
                tokenFetcher,
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC")),
                Duration.ofSeconds(60)
        );
        var oauthFetcher = new CapturingOauthFetcher();

        var client = new OpenApiFeishuIdentityClient(properties, tokenService, oauthFetcher, new CapturingMessageSender());

        assertThat(client.exchangeCodeForFeishuUserId("real-login-code")).isEqualTo("ou_real_user_001");
        assertThat(tokenFetcher.fetchCount).isEqualTo(1);
        assertThat(oauthFetcher.tenantAccessToken).isEqualTo("tenant-token-001");
        assertThat(oauthFetcher.code).isEqualTo("real-login-code");
    }

    @Test
    void sendsNotificationThroughTenantTokenAndMessageSender() {
        var properties = openApiProperties();
        var tokenFetcher = new FixedTokenFetcher();
        var tokenService = new FeishuTenantAccessTokenService(
                properties,
                tokenFetcher,
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC")),
                Duration.ofSeconds(60)
        );
        var messageSender = new CapturingMessageSender();
        var client = new OpenApiFeishuIdentityClient(
                properties,
                tokenService,
                new CapturingOauthFetcher(),
                messageSender
        );

        var result = client.sendNotification(notification());

        assertThat(result.success()).isTrue();
        assertThat(tokenFetcher.fetchCount).isEqualTo(1);
        assertThat(messageSender.tenantAccessToken).isEqualTo("tenant-token-001");
        assertThat(messageSender.notification.toModel().recipientFeishuUserId()).isEqualTo("ou_rnd_001");
    }

    private FeishuProperties openApiProperties() {
        var properties = new FeishuProperties();
        properties.setMode("OPENAPI");
        properties.setAppId("cli_test");
        properties.setAppSecret("secret");
        return properties;
    }

    private static class FixedTokenFetcher implements FeishuTenantAccessTokenFetcher {
        private int fetchCount;

        @Override
        public FeishuTenantAccessToken fetch(FeishuProperties properties) {
            fetchCount++;
            return new FeishuTenantAccessToken("tenant-token-001", 7200);
        }
    }

    private static class CapturingOauthFetcher implements FeishuOauthUserInfoFetcher {
        private String tenantAccessToken;
        private String code;

        @Override
        public FeishuOauthUserInfo fetch(FeishuProperties properties, String tenantAccessToken, String code) {
            this.tenantAccessToken = tenantAccessToken;
            this.code = code;
            return new FeishuOauthUserInfo("ou_real_user_001", "ou_open_001", "on_union_001");
        }
    }

    private static class CapturingMessageSender implements FeishuMessageSender {
        private String tenantAccessToken;
        private FeishuNotificationEntity notification;

        @Override
        public FeishuSendResult send(
                FeishuProperties properties,
                String tenantAccessToken,
                FeishuNotificationEntity notification
        ) {
            this.tenantAccessToken = tenantAccessToken;
            this.notification = notification;
            return FeishuSendResult.sent();
        }
    }

    private FeishuNotificationEntity notification() {
        return new FeishuNotificationEntity(
                "FSN-001",
                "RND_TASK",
                "TASK-001",
                "USR-001",
                "ou_rnd_001",
                "RND_TASK_ASSIGNED",
                "研发任务分发通知",
                "香卤大肠头 A0 已分发给你",
                "PENDING_SEND",
                LocalDateTime.of(2026, 6, 19, 10, 0),
                null
        );
    }
}
