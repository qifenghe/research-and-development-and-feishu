package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuTenantAccessTokenServiceTest {

    @Test
    void cachesTenantAccessTokenUntilRefreshWindow() {
        var clock = new MutableClock(Instant.parse("2026-06-19T00:00:00Z"));
        var fetcher = new CountingTokenFetcher();
        var service = new FeishuTenantAccessTokenService(openApiProperties(), fetcher, clock, Duration.ofSeconds(60));

        assertThat(service.currentToken()).isEqualTo("tenant-token-1");
        assertThat(service.currentToken()).isEqualTo("tenant-token-1");
        assertThat(fetcher.fetchCount).isEqualTo(1);

        clock.advance(Duration.ofSeconds(7_000));
        assertThat(service.currentToken()).isEqualTo("tenant-token-1");
        assertThat(fetcher.fetchCount).isEqualTo(1);

        clock.advance(Duration.ofSeconds(141));
        assertThat(service.currentToken()).isEqualTo("tenant-token-2");
        assertThat(fetcher.fetchCount).isEqualTo(2);
    }

    @Test
    void rejectsTenantAccessTokenFetchWhenOpenApiConfigurationIsIncomplete() {
        var properties = new FeishuProperties();
        properties.setMode("OPENAPI");
        properties.setAppId("cli_xxx");

        var service = new FeishuTenantAccessTokenService(
                properties,
                new CountingTokenFetcher(),
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC")),
                Duration.ofSeconds(60)
        );

        assertThatThrownBy(service::currentToken)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FEISHU_OPENAPI_CONFIG_INCOMPLETE");
    }

    private FeishuProperties openApiProperties() {
        var properties = new FeishuProperties();
        properties.setMode("OPENAPI");
        properties.setAppId("cli_test_app");
        properties.setAppSecret("test-secret");
        return properties;
    }

    private static class CountingTokenFetcher implements FeishuTenantAccessTokenFetcher {
        private int fetchCount;

        @Override
        public FeishuTenantAccessToken fetch(FeishuProperties properties) {
            fetchCount++;
            return new FeishuTenantAccessToken("tenant-token-" + fetchCount, 7_200);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
