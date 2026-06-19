package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record UserAccount(
        String id,
        String name,
        String feishuUserId,
        String role,
        String departmentName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
