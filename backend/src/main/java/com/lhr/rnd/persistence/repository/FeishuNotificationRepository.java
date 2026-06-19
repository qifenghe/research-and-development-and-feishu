package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.FeishuNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeishuNotificationRepository extends JpaRepository<FeishuNotificationEntity, String> {
    List<FeishuNotificationEntity> findByStatusOrderByCreatedAtAsc(String status);
}
