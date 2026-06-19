package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.FeishuNotification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "feishu_notification")
public class FeishuNotificationEntity {
    @Id
    private String id;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "recipient_user_id", nullable = false)
    private String recipientUserId;

    @Column(name = "recipient_feishu_user_id", nullable = false)
    private String recipientFeishuUserId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected FeishuNotificationEntity() {
    }

    public FeishuNotificationEntity(
            String id,
            String businessType,
            String businessId,
            String recipientUserId,
            String recipientFeishuUserId,
            String templateKey,
            String title,
            String content,
            String status,
            LocalDateTime createdAt,
            LocalDateTime sentAt
    ) {
        this.id = id;
        this.businessType = businessType;
        this.businessId = businessId;
        this.recipientUserId = recipientUserId;
        this.recipientFeishuUserId = recipientFeishuUserId;
        this.templateKey = templateKey;
        this.title = title;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public FeishuNotification toModel() {
        return new FeishuNotification(
                id,
                businessType,
                businessId,
                recipientUserId,
                recipientFeishuUserId,
                templateKey,
                title,
                content,
                status,
                createdAt,
                sentAt
        );
    }
}
