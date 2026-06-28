package com.lhr.rnd.service;

public record FeishuIntegrationStatus(
        String mode,
        String baseUrl,
        String appId,
        boolean appIdConfigured,
        boolean appSecretConfigured,
        boolean readyForOpenApi
) {
}
