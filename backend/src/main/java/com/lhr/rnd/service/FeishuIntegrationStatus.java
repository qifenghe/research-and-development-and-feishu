package com.lhr.rnd.service;

public record FeishuIntegrationStatus(
        String mode,
        String baseUrl,
        boolean appIdConfigured,
        boolean appSecretConfigured,
        boolean readyForOpenApi
) {
}
