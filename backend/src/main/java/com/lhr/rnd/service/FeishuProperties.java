package com.lhr.rnd.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rnd.feishu")
public class FeishuProperties {
    private boolean enabled;
    private String mode = "MOCK";
    private String baseUrl = "https://open.feishu.cn";
    private String appUrl = "http://127.0.0.1:4174/";
    private String cardActionSecret;
    private String appId;
    private String appSecret;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public void setAppUrl(String appUrl) {
        this.appUrl = appUrl;
    }

    public String getCardActionSecret() {
        return cardActionSecret;
    }

    public void setCardActionSecret(String cardActionSecret) {
        this.cardActionSecret = cardActionSecret;
    }

    public boolean cardActionSecretConfigured() {
        return cardActionSecret != null && !cardActionSecret.isBlank();
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String normalizedMode() {
        if (mode == null || mode.isBlank()) {
            return "MOCK";
        }
        return mode.trim().toUpperCase();
    }

    public boolean appIdConfigured() {
        return appId != null && !appId.isBlank();
    }

    public boolean appSecretConfigured() {
        return appSecret != null && !appSecret.isBlank();
    }

    public boolean readyForOpenApi() {
        return "OPENAPI".equals(normalizedMode()) && appIdConfigured() && appSecretConfigured();
    }
}
