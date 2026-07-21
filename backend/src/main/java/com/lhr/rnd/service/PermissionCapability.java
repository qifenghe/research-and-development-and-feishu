package com.lhr.rnd.service;

public record PermissionCapability(
        String moduleCode,
        String moduleName,
        String actionCode,
        String actionName,
        String httpMethod,
        String pathPattern,
        int sortOrder
) {
}
