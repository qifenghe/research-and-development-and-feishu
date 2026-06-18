package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record ShipmentRecord(
        String id,
        String versionId,
        String sampleNo,
        String productName,
        String versionCode,
        Integer quantity,
        String receiverName,
        String trackingNo,
        String remark,
        ShipmentStatus status,
        LocalDateTime shippedAt
) {
    public ShipmentRecord withStatus(ShipmentStatus nextStatus) {
        return new ShipmentRecord(
                id,
                versionId,
                sampleNo,
                productName,
                versionCode,
                quantity,
                receiverName,
                trackingNo,
                remark,
                nextStatus,
                shippedAt
        );
    }
}
