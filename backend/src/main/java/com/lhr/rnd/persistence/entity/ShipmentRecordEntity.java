package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipment_record")
public class ShipmentRecordEntity {
    @Id
    private String id;

    @Column(name = "version_id", nullable = false)
    private String versionId;

    @Column(name = "sample_no", nullable = false)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "version_code", nullable = false)
    private String versionCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "tracking_no", nullable = false)
    private String trackingNo;

    @Column(name = "remark")
    private String remark;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "shipped_at", nullable = false)
    private LocalDateTime shippedAt;

    protected ShipmentRecordEntity() {
    }

    public ShipmentRecordEntity(
            String id,
            String versionId,
            String sampleNo,
            String productName,
            String versionCode,
            Integer quantity,
            String receiverName,
            String trackingNo,
            String remark,
            String status,
            LocalDateTime shippedAt
    ) {
        this.id = id;
        this.versionId = versionId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.versionCode = versionCode;
        this.quantity = quantity;
        this.receiverName = receiverName;
        this.trackingNo = trackingNo;
        this.remark = remark;
        this.status = status;
        this.shippedAt = shippedAt;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
