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

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "feishu_user_id", nullable = false)
    private String feishuUserId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "status", nullable = false)
    private String status;

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
        this.id = id;
        this.name = name;
        this.feishuUserId = feishuUserId;
        this.role = role;
        this.departmentName = departmentName;
        this.status = status;
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

    public void updateStatus(String status, LocalDateTime updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFeishuUserId() {
        return feishuUserId;
    }

    public UserAccount toModel() {
        return new UserAccount(id, name, feishuUserId, role, departmentName, status, createdAt, updatedAt);
    }
}
