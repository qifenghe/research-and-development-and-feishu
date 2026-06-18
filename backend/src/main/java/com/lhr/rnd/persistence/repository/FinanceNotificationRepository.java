package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.FinanceNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceNotificationRepository extends JpaRepository<FinanceNotificationEntity, String> {
}
