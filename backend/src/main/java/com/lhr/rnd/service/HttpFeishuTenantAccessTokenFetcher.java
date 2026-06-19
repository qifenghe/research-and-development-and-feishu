package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpFeishuTenantAccessTokenFetcher implements FeishuTenantAccessTokenFetcher {
    private static final String TOKEN_PATH = "/open-apis/auth/v3/tenant_access_token/internal";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpFeishuTenantAccessTokenFetcher() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    HttpFeishuTenantAccessTokenFetcher(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeishuTenantAccessToken fetch(FeishuProperties properties) {
        var requestBody = """
                {"app_id":"%s","app_secret":"%s"}
                """.formatted(properties.getAppId(), properties.getAppSecret()).trim();
        var request = HttpRequest.newBuilder(tokenUri(properties))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        "FEISHU_TENANT_TOKEN_FETCH_FAILED",
                        "飞书 tenant_access_token 获取失败，HTTP 状态码：" + response.statusCode()
                );
            }
            return parseResponse(response.body());
        } catch (IOException exception) {
            throw new BusinessException("FEISHU_TENANT_TOKEN_FETCH_FAILED", "飞书 tenant_access_token 获取失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("FEISHU_TENANT_TOKEN_FETCH_FAILED", "飞书 tenant_access_token 获取被中断");
        }
    }

    private URI tokenUri(FeishuProperties properties) {
        var baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + TOKEN_PATH);
    }

    private FeishuTenantAccessToken parseResponse(String responseBody) throws IOException {
        var root = objectMapper.readTree(responseBody);
        var code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new BusinessException(
                    "FEISHU_TENANT_TOKEN_FETCH_FAILED",
                    "飞书 tenant_access_token 获取失败：" + root.path("msg").asText("未知错误")
            );
        }
        var token = root.path("tenant_access_token").asText("");
        var expiresInSeconds = root.path("expire").asLong(0);
        if (token.isBlank() || expiresInSeconds <= 0) {
            throw new BusinessException("FEISHU_TENANT_TOKEN_RESPONSE_INVALID", "飞书 tenant_access_token 响应格式不完整");
        }
        return new FeishuTenantAccessToken(token, expiresInSeconds);
    }
}
