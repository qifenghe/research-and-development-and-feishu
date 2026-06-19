package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFeishuOauthUserInfoFetcherTest {

    @Test
    void exchangesOauthCodeForFeishuUserIdentity() {
        var httpClient = new CapturingHttpClient(200, """
                {
                  "code": 0,
                  "msg": "ok",
                  "data": {
                    "user_id": "ou_real_user_001",
                    "open_id": "ou_open_001",
                    "union_id": "on_union_001"
                  }
                }
                """);

        var result = new HttpFeishuOauthUserInfoFetcher(httpClient, new ObjectMapper())
                .fetch(openApiProperties(), "tenant-token-001", "login-code-001");

        assertThat(result.feishuUserId()).isEqualTo("ou_real_user_001");
        assertThat(result.openId()).isEqualTo("ou_open_001");
        assertThat(result.unionId()).isEqualTo("on_union_001");
        assertThat(httpClient.request.get().method()).isEqualTo("POST");
        assertThat(httpClient.request.get().uri().toString())
                .isEqualTo("https://open.feishu.cn/open-apis/authen/v1/access_token");
        assertThat(httpClient.request.get().headers().firstValue("Authorization"))
                .contains("Bearer tenant-token-001");
        assertThat(httpClient.requestBody.get()).contains("\"grant_type\":\"authorization_code\"");
        assertThat(httpClient.requestBody.get()).contains("\"code\":\"login-code-001\"");
    }

    @Test
    void usesOpenIdWhenUserIdIsMissing() {
        var httpClient = new CapturingHttpClient(200, """
                {
                  "code": 0,
                  "msg": "ok",
                  "data": {
                    "open_id": "ou_open_only"
                  }
                }
                """);

        var result = new HttpFeishuOauthUserInfoFetcher(httpClient, new ObjectMapper())
                .fetch(openApiProperties(), "tenant-token-001", "login-code-001");

        assertThat(result.feishuUserId()).isEqualTo("ou_open_only");
    }

    @Test
    void throwsBusinessErrorWhenFeishuReturnsErrorCode() {
        var httpClient = new CapturingHttpClient(200, """
                {
                  "code": 99991672,
                  "msg": "invalid authorization code"
                }
                """);

        assertThatThrownBy(() -> new HttpFeishuOauthUserInfoFetcher(httpClient, new ObjectMapper())
                .fetch(openApiProperties(), "tenant-token-001", "bad-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid authorization code")
                .extracting("code")
                .isEqualTo("FEISHU_OAUTH_USER_FETCH_FAILED");
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
