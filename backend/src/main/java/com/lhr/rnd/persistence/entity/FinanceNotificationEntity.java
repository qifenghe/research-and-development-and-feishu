package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "finance_notification")
public class FinanceNotificationEntity {
    @Id
    private String id;

    @Column(name = "pricing_file_id", nullable = false)
    private String pricingFileId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "remark")
    private String remark;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    protected FinanceNotificationEntity() {
    }

    public FinanceNotificationEntity(
            String id,
            String pricingFileId,
            String recipientName,
            String remark,
            String status,
            LocalDateTime notifiedAt
    ) {
        this.id = id;
        this.pricingFileId = pricingFileId;
        this.recipientName = recipientName;
        this.remark = remark;
        this.status = status;
        this.notifiedAt = notifiedAt;
    }
}
