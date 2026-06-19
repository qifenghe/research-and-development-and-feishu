package com.lhr.rnd.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

        var client = new OpenApiFeishuIdentityClient(properties, tokenService, oauthFetcher);

        assertThat(client.exchangeCodeForFeishuUserId("real-login-code")).isEqualTo("ou_real_user_001");
        assertThat(tokenFetcher.fetchCount).isEqualTo(1);
        assertThat(oauthFetcher.tenantAccessToken).isEqualTo("tenant-token-001");
        assertThat(oauthFetcher.code).isEqualTo("real-login-code");
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
}
