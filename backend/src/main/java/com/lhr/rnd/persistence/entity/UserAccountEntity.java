package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class UserAccountEntity {
    @Id
    private String id;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "feishu_user_id", unique = true)
    private String feishuUserId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(
            String id,
            String name,
            String feishuUserId,
            String role,
            String departmentName,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                null,
                null,
                name,
                feishuUserId,
                role,
                departmentName,
                status,
                null,
                createdAt,
                updatedAt
        );
    }

    public UserAccountEntity(
            String id,
            String username,
            String passwordHash,
            String name,
            String feishuUserId,
            String role,
            String departmentName,
            String status,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.feishuUserId = feishuUserId;
        this.role = role;
        this.departmentName = departmentName;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String name, String role, String departmentName, LocalDateTime updatedAt) {
        this.name = name;
        this.role = role;
        this.departmentName = departmentName;
        this.status = "ACTIVE";
        this.updatedAt = updatedAt;
    }

    public void updateAccount(String username, String name, String feishuUserId, String role, String departmentName, LocalDateTime updatedAt) {
        this.username = username;
        this.name = name;
        this.feishuUserId = feishuUserId;
        this.role = role;
        this.departmentName = departmentName;
        this.status = "ACTIVE";
        this.updatedAt = updatedAt;
    }

    public void updatePasswordHash(String passwordHash, LocalDateTime updatedAt) {
        this.passwordHash = passwordHash;
        this.updatedAt = updatedAt;
    }

    public void updateStatus(String status, LocalDateTime updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void markLoggedIn(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
        this.updatedAt = lastLoginAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFeishuUserId() {
        return feishuUserId;
    }

    public UserAccount toModel() {
        return new UserAccount(id, username, name, feishuUserId, role, departmentName, status, lastLoginAt, createdAt, updatedAt);
    }
}
