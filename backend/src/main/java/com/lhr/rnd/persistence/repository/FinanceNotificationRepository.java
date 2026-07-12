package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.FinanceNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinanceNotificationRepository extends JpaRepository<FinanceNotificationEntity, String> {
    Optional<FinanceNotificationEntity> findFirstByPricingFileIdOrderByNotifiedAtDesc(String pricingFileId);
}
