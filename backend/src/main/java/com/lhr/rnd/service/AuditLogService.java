package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.AuditLogEntity;
import com.lhr.rnd.persistence.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            String businessType,
            String businessId,
            String action,
            String operatorName,
            String detail
    ) {
        auditLogRepository.save(new AuditLogEntity(
                "AUD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                businessType,
                businessId,
                action,
                operatorName,
                detail,
                LocalDateTime.now(clock)
        ));
    }

    public List<AuditLogEntity> recent() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }
}
