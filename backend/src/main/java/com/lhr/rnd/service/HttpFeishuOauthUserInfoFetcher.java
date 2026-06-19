package com.lhr.rnd.service;

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
public class HttpFeishuOauthUserInfoFetcher implements FeishuOauthUserInfoFetcher {
    private static final String ACCESS_TOKEN_PATH = "/open-apis/authen/v1/access_token";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpFeishuOauthUserInfoFetcher() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    HttpFeishuOauthUserInfoFetcher(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeishuOauthUserInfo fetch(FeishuProperties properties, String tenantAccessToken, String code) {
        var requestBody = """
                {"grant_type":"authorization_code","code":"%s"}
                """.formatted(code).trim();
        var request = HttpRequest.newBuilder(accessTokenUri(properties))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + tenantAccessToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        "FEISHU_OAUTH_USER_FETCH_FAILED",
                        "飞书免登用户身份获取失败，HTTP 状态码：" + response.statusCode()
                );
            }
            return parseResponse(response.body());
        } catch (IOException exception) {
            throw new BusinessException("FEISHU_OAUTH_USER_FETCH_FAILED", "飞书免登用户身份获取失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("FEISHU_OAUTH_USER_FETCH_FAILED", "飞书免登用户身份获取被中断");
        }
    }

    private URI accessTokenUri(FeishuProperties properties) {
        var baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + ACCESS_TOKEN_PATH);
    }

    private FeishuOauthUserInfo parseResponse(String responseBody) throws IOException {
        var root = objectMapper.readTree(responseBody);
        var code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new BusinessException(
                    "FEISHU_OAUTH_USER_FETCH_FAILED",
                    "飞书免登用户身份获取失败：" + root.path("msg").asText("未知错误")
            );
        }
        var data = root.path("data");
        var userId = data.path("user_id").asText("");
        var openId = data.path("open_id").asText("");
        var unionId = data.path("union_id").asText("");
        var feishuUserId = userId.isBlank() ? openId : userId;
        if (feishuUserId.isBlank()) {
            throw new BusinessException("FEISHU_OAUTH_USER_RESPONSE_INVALID", "飞书免登用户身份响应格式不完整");
        }
        return new FeishuOauthUserInfo(feishuUserId, openId, unionId);
    }
}
