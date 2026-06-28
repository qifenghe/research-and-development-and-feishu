package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {
    List<AuditLogEntity> findTop100ByOrderByCreatedAtDesc();
}
