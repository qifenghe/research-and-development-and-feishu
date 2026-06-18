package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_feedback")
public class CustomerFeedbackEntity {
    @Id
    private String id;

    @Column(name = "shipment_id", nullable = false)
    private String shipmentId;

    @Column(name = "feedback_by", nullable = false)
    private String feedbackBy;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "comment")
    private String comment;

    @Column(name = "feedback_at", nullable = false)
    private LocalDateTime feedbackAt;

    protected CustomerFeedbackEntity() {
    }

    public CustomerFeedbackEntity(
            String id,
            String shipmentId,
            String feedbackBy,
            String result,
            String comment,
            LocalDateTime feedbackAt
    ) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.feedbackBy = feedbackBy;
        this.result = result;
        this.comment = comment;
        this.feedbackAt = feedbackAt;
    }
}
