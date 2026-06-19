package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_permission")
public class RolePermissionEntity {
    @Id
    private String id;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RolePermissionEntity() {
    }

    public RolePermissionEntity(
            String id,
            String roleCode,
            String httpMethod,
            String pathPattern,
            boolean enabled,
            String description,
            int sortOrder,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.roleCode = roleCode;
        this.httpMethod = httpMethod;
        this.pathPattern = pathPattern;
        this.enabled = enabled;
        this.description = description;
        this.sortOrder = sortOrder;
        this.updatedAt = updatedAt;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
