package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTokenServiceTest {

    @Test
    void issuesAndVerifiesSignedSessionToken() {
        var service = new SessionTokenService(
                sessionProperties(),
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC"))
        );

        var token = service.issue(user());
        var session = service.verify(token);

        assertThat(token).doesNotStartWith("mock-token-");
        assertThat(session.userId()).isEqualTo("USR-001");
        assertThat(session.username()).isEqualTo("rnd_engineer");
        assertThat(session.name()).isEqualTo("张研发");
        assertThat(session.feishuUserId()).isEqualTo("ou_rnd_001");
        assertThat(session.role()).isEqualTo("RND_ENGINEER");
        assertThat(session.expiresAt()).isEqualTo(Instant.parse("2026-06-20T00:00:00Z"));
    }

    @Test
    void rejectsTamperedSessionToken() {
        var service = new SessionTokenService(
                sessionProperties(),
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC"))
        );

        var token = service.issue(user());
        var tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SESSION_TOKEN_INVALID");
    }

    @Test
    void rejectsExpiredSessionToken() {
        var issuingClock = Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC"));
        var verifyingClock = Clock.fixed(Instant.parse("2026-06-20T00:00:01Z"), ZoneId.of("UTC"));
        var token = new SessionTokenService(sessionProperties(), issuingClock).issue(user());

        assertThatThrownBy(() -> new SessionTokenService(sessionProperties(), verifyingClock).verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SESSION_TOKEN_EXPIRED");
    }

    private SessionProperties sessionProperties() {
        var properties = new SessionProperties();
        properties.setSecret("test-session-secret-with-enough-length");
        properties.setTtlSeconds(86_400);
        return properties;
    }

    private UserAccount user() {
        return new UserAccount(
                "USR-001",
                "rnd_engineer",
                "张研发",
                "ou_rnd_001",
                "RND_ENGINEER",
                "研发部",
                "ACTIVE",
                null,
                LocalDateTime.of(2026, 6, 19, 8, 0),
                LocalDateTime.of(2026, 6, 19, 8, 0)
        );
    }
}
