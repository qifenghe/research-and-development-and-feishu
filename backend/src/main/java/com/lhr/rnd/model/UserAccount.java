package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record UserAccount(
        String id,
        String username,
        String name,
        String feishuUserId,
        String role,
        String departmentName,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
