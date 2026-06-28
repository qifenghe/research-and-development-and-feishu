package com.lhr.rnd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpFeishuMessageSender implements FeishuMessageSender {
    private static final String MESSAGE_PATH = "/open-apis/im/v1/messages?receive_id_type=user_id";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpFeishuMessageSender() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    HttpFeishuMessageSender(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeishuSendResult send(FeishuProperties properties, String tenantAccessToken, FeishuNotificationEntity notification) {
        var request = HttpRequest.newBuilder(messageUri(properties))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + tenantAccessToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(properties, notification)))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return FeishuSendResult.failed("飞书消息发送失败，HTTP 状态码：" + response.statusCode());
            }
            return parseSendResult(response.body());
        } catch (IOException exception) {
            return FeishuSendResult.failed("飞书消息发送失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FeishuSendResult.failed("飞书消息发送被中断");
        }
    }

    private String requestBody(FeishuProperties properties, FeishuNotificationEntity notification) {
        var model = notification.toModel();
        try {
            var content = objectMapper.writeValueAsString(interactiveCard(properties, notification));
            return objectMapper.writeValueAsString(Map.of(
                    "receive_id", model.recipientFeishuUserId(),
                    "msg_type", "interactive",
                    "content", content
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("飞书消息体序列化失败", exception);
        }
    }

    private Map<String, Object> interactiveCard(FeishuProperties properties, FeishuNotificationEntity notification) {
        var model = notification.toModel();
        return Map.of(
                "config", Map.of("wide_screen_mode", true),
                "header", Map.of(
                        "template", "blue",
                        "title", Map.of("tag", "plain_text", "content", model.title())
                ),
                "elements", java.util.List.of(
                        Map.of("tag", "div", "text", Map.of(
                                "tag", "lark_md",
                                "content", model.content() == null ? "" : model.content()
                        )),
                        Map.of(
                                "tag", "action",
                                "actions", java.util.List.of(
                                        Map.of(
                                                "tag", "button",
                                                "text", Map.of("tag", "plain_text", "content", "查看详情"),
                                                "type", "primary",
                                                "url", businessUrl(properties, model.businessType(), model.businessId())
                                        ),
                                        Map.of(
                                                "tag", "button",
                                                "text", Map.of("tag", "plain_text", "content", "接受任务"),
                                                "type", "default",
                                                "value", Map.of(
                                                        "action", actionKey(model.templateKey()),
                                                        "businessType", model.businessType(),
                                                        "businessId", model.businessId(),
                                                        "notificationId", model.id()
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private String businessUrl(FeishuProperties properties, String businessType, String businessId) {
        var baseUrl = properties.getAppUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if ("RND_TASK".equals(businessType)) {
            return "%s/m/tasks/%s".formatted(baseUrl, businessId);
        }
        return "%s/admin/dashboard".formatted(baseUrl);
    }

    private String actionKey(String templateKey) {
        if ("RND_TASK_ASSIGNED".equals(templateKey)) {
            return "ACCEPT_RND_TASK";
        }
        return "VIEW_NOTIFICATION";
    }

    private URI messageUri(FeishuProperties properties) {
        var baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + MESSAGE_PATH);
    }

    private FeishuSendResult parseSendResult(String responseBody) throws IOException {
        var root = objectMapper.readTree(responseBody);
        var code = root.path("code").asInt(-1);
        if (code != 0) {
            return FeishuSendResult.failed("飞书消息发送失败：" + root.path("msg").asText("未知错误"));
        }
        return FeishuSendResult.sent();
    }
}
