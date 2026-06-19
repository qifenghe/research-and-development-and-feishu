package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpFeishuMessageSenderTest {

    @Test
    void sendsTextMessageToFeishuUser() {
        var httpClient = new CapturingHttpClient(200, """
                {"code":0,"msg":"ok","data":{"message_id":"om_message_001"}}
                """);

        var result = new HttpFeishuMessageSender(httpClient, new ObjectMapper())
                .send(openApiProperties(), "tenant-token-001", notification());

        assertThat(result.success()).isTrue();
        assertThat(httpClient.request.get().method()).isEqualTo("POST");
        assertThat(httpClient.request.get().uri().toString())
                .isEqualTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=user_id");
        assertThat(httpClient.request.get().headers().firstValue("Authorization"))
                .contains("Bearer tenant-token-001");
        assertThat(httpClient.requestBody.get()).contains("\"receive_id\":\"ou_rnd_001\"");
        assertThat(httpClient.requestBody.get()).contains("\"msg_type\":\"text\"");
        assertThat(httpClient.requestBody.get()).contains("研发任务分发通知");
        assertThat(httpClient.requestBody.get()).contains("香卤大肠头 A0 已分发给你");
    }

    @Test
    void returnsFailedResultWhenFeishuRejectsMessage() {
        var httpClient = new CapturingHttpClient(200, """
                {"code":230001,"msg":"permission denied"}
                """);

        var result = new HttpFeishuMessageSender(httpClient, new ObjectMapper())
                .send(openApiProperties(), "tenant-token-001", notification());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("permission denied");
    }

    private FeishuNotificationEntity notification() {
        return new FeishuNotificationEntity(
                "FSN-001",
                "RND_TASK",
                "TASK-001",
                "USR-001",
                "ou_rnd_001",
                "RND_TASK_ASSIGNED",
                "研发任务分发通知",
                "香卤大肠头 A0 已分发给你，请在飞书自建应用中接受任务。",
                "PENDING_SEND",
                LocalDateTime.of(2026, 6, 19, 10, 0),
                null
        );
    }

    private FeishuProperties openApiProperties() {
        var properties = new FeishuProperties();
        properties.setMode("OPENAPI");
        properties.setBaseUrl("https://open.feishu.cn");
        properties.setAppId("cli_http_test");
        properties.setAppSecret("http-secret");
        return properties;
    }

    private static class CapturingHttpClient extends HttpClient {
        private final int statusCode;
        private final String responseBody;
        private final AtomicReference<HttpRequest> request = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private CapturingHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            this.request.set(request);
            this.requestBody.set(readRequestBody(request));
            @SuppressWarnings("unchecked")
            var response = (HttpResponse<T>) new StringHttpResponse(statusCode, responseBody, request);
            return response;
        }

        private String readRequestBody(HttpRequest request) {
            var output = new ByteArrayOutputStream();
            var completed = new CompletableFuture<Void>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    var bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    completed.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    completed.complete(null);
                }
            });
            try {
                completed.get(1, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record StringHttpResponse(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type", java.util.List.of("application/json")), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
