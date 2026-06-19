package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class FeishuTenantAccessTokenService {
    private final FeishuProperties properties;
    private final FeishuTenantAccessTokenFetcher fetcher;
    private final Clock clock;
    private final Duration refreshBeforeExpiry;
    private CachedToken cachedToken;

    @Autowired
    public FeishuTenantAccessTokenService(
            FeishuProperties properties,
            FeishuTenantAccessTokenFetcher fetcher
    ) {
        this(properties, fetcher, Clock.systemDefaultZone(), Duration.ofSeconds(60));
    }

    FeishuTenantAccessTokenService(
            FeishuProperties properties,
            FeishuTenantAccessTokenFetcher fetcher,
            Clock clock,
            Duration refreshBeforeExpiry
    ) {
        this.properties = properties;
        this.fetcher = fetcher;
        this.clock = clock;
        this.refreshBeforeExpiry = refreshBeforeExpiry;
    }

    public synchronized String currentToken() {
        ensureReadyForOpenApi();
        var now = clock.instant();
        if (cachedToken != null && cachedToken.usableAt(now, refreshBeforeExpiry)) {
            return cachedToken.token();
        }

        var fetched = fetcher.fetch(properties);
        cachedToken = new CachedToken(
                fetched.token(),
                now.plusSeconds(fetched.expiresInSeconds())
        );
        return cachedToken.token();
    }

    private void ensureReadyForOpenApi() {
        if (!properties.readyForOpenApi()) {
            throw new BusinessException("FEISHU_OPENAPI_CONFIG_INCOMPLETE", "飞书 OpenAPI 配置未完成");
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
        private boolean usableAt(Instant now, Duration refreshBeforeExpiry) {
            return now.isBefore(expiresAt.minus(refreshBeforeExpiry));
        }
    }
}
