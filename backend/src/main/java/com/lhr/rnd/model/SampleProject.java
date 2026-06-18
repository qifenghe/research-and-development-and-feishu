package com.lhr.rnd.model;

import com.lhr.rnd.domain.SampleStatus;

import java.time.LocalDateTime;

public record SampleProject(
        String id,
        String sampleNo,
        String productName,
        String productType,
        String customerName,
        String specification,
        SampleStatus status,
        LocalDateTime createdAt
) {
}
