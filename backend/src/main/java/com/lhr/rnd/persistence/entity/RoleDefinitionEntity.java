package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.RoleDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_definition")
public class RoleDefinitionEntity {
    @Id
    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "system_builtin", nullable = false)
    private boolean systemBuiltin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RoleDefinitionEntity() {
    }

    public RoleDefinitionEntity(
            String roleCode,
            String roleName,
            String description,
            String status,
            boolean systemBuiltin,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.status = status;
        this.systemBuiltin = systemBuiltin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSystemBuiltin() {
        return systemBuiltin;
    }

    public void update(String roleName, String description, LocalDateTime updatedAt) {
        this.roleName = roleName;
        this.description = description;
        this.updatedAt = updatedAt;
    }

    public void updateStatus(String status, LocalDateTime updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public RoleDefinition toModel() {
        return new RoleDefinition(roleCode, roleName, description, status, systemBuiltin, createdAt, updatedAt);
    }
}
