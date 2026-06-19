package com.lhr.rnd.service;

import java.time.Instant;

public record SessionPrincipal(
        String userId,
        String name,
        String feishuUserId,
        String role,
        Instant expiresAt
) {
}
