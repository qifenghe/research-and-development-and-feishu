package com.lhr.rnd.service;

import java.time.Instant;

public record SessionPrincipal(
        String userId,
        String username,
        String name,
        String feishuUserId,
        String role,
        Instant expiresAt
) {
}
