package com.lhr.rnd.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rnd.session")
public class SessionProperties {
    private String secret = "local-dev-session-secret-change-me";
    private long ttlSeconds = 86_400;
    private boolean authRequired = true;
    private boolean testBusinessApiAuthenticationBypass;

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

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    public boolean isTestBusinessApiAuthenticationBypass() {
        return testBusinessApiAuthenticationBypass;
    }

    public void setTestBusinessApiAuthenticationBypass(boolean testBusinessApiAuthenticationBypass) {
        this.testBusinessApiAuthenticationBypass = testBusinessApiAuthenticationBypass;
    }
}
