package com.lhr.rnd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class SessionTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SessionProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    @Autowired
    public SessionTokenService(SessionProperties properties) {
        this(properties, Clock.systemUTC(), new ObjectMapper());
    }

    SessionTokenService(SessionProperties properties, Clock clock) {
        this(properties, clock, new ObjectMapper());
    }

    SessionTokenService(SessionProperties properties, Clock clock, ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public String issue(UserAccount user) {
        var expiresAt = clock.instant().plusSeconds(properties.getTtlSeconds());
        var payload = encodeJson(Map.of(
                "userId", user.id(),
                "name", user.name(),
                "feishuUserId", user.feishuUserId(),
                "role", user.role()
        ));
        var expiry = encoder.encodeToString(Long.toString(expiresAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        var unsigned = payload + "." + expiry;
        return unsigned + "." + sign(unsigned);
    }

    public SessionPrincipal verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("SESSION_TOKEN_REQUIRED", "登录凭证不能为空");
        }
        var parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException("SESSION_TOKEN_INVALID", "登录凭证格式不正确");
        }
        var unsigned = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsigned).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("SESSION_TOKEN_INVALID", "登录凭证签名无效");
        }
        var expiresAt = Instant.ofEpochSecond(parseExpiry(parts[1]));
        if (!expiresAt.isAfter(clock.instant())) {
            throw new BusinessException("SESSION_TOKEN_EXPIRED", "登录凭证已过期");
        }
        return parsePayload(parts[0], expiresAt);
    }

    private String encodeJson(Map<String, String> payload) {
        try {
            return encoder.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("登录凭证序列化失败", exception);
        }
    }

    private SessionPrincipal parsePayload(String encodedPayload, Instant expiresAt) {
        try {
            var root = objectMapper.readTree(decoder.decode(encodedPayload));
            return new SessionPrincipal(
                    root.path("userId").asText(),
                    root.path("name").asText(),
                    root.path("feishuUserId").asText(),
                    root.path("role").asText(),
                    expiresAt
            );
        } catch (Exception exception) {
            throw new BusinessException("SESSION_TOKEN_INVALID", "登录凭证内容无效");
        }
    }

    private long parseExpiry(String encodedExpiry) {
        try {
            return Long.parseLong(new String(decoder.decode(encodedExpiry), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BusinessException("SESSION_TOKEN_INVALID", "登录凭证过期时间无效");
        }
    }

    private String sign(String unsigned) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("登录凭证签名失败", exception);
        }
    }
}
