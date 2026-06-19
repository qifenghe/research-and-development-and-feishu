package com.lhr.rnd.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rnd.session")
public class SessionProperties {
    private String secret = "local-dev-session-secret-change-me";
    private long ttlSeconds = 86_400;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
