package com.lhr.rnd.service;

public record RolePermissionRule(
        String id,
        String httpMethod,
        String pathPattern,
        boolean enabled,
        String description,
        int sortOrder
) {
}
