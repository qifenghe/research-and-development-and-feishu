package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record RoleDefinition(
        String roleCode,
        String roleName,
        String description,
        String status,
        boolean systemBuiltin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
