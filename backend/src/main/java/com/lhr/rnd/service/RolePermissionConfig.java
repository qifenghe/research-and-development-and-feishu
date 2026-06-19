package com.lhr.rnd.service;

import java.util.List;

public record RolePermissionConfig(
        String roleCode,
        List<RolePermissionRule> permissions
) {
}
