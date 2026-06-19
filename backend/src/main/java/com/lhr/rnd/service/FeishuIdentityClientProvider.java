package com.lhr.rnd.service;

import org.springframework.stereotype.Component;

@Component
public class FeishuIdentityClientProvider {
    private final FeishuProperties properties;
    private final FeishuIdentityClient mockClient;
    private final FeishuIdentityClient openApiClient;

    public FeishuIdentityClientProvider(
            FeishuProperties properties,
            FeishuTenantAccessTokenService tenantAccessTokenService
    ) {
        this.properties = properties;
        this.mockClient = new MockFeishuIdentityClient();
        this.openApiClient = new OpenApiFeishuIdentityClient(properties, tenantAccessTokenService);
    }

    public FeishuIdentityClient current() {
        if ("OPENAPI".equals(properties.normalizedMode())) {
            return openApiClient;
        }
        return mockClient;
    }

    public FeishuIntegrationStatus status() {
        return new FeishuIntegrationStatus(
                properties.normalizedMode(),
                properties.getBaseUrl(),
                properties.appIdConfigured(),
                properties.appSecretConfigured(),
                properties.readyForOpenApi()
        );
    }
}
