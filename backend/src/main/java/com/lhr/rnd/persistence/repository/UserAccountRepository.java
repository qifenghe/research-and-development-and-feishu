package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {
    Optional<UserAccountEntity> findByFeishuUserId(String feishuUserId);

    Optional<UserAccountEntity> findFirstByNameAndStatus(String name, String status);

    List<UserAccountEntity> findAllByOrderByCreatedAtDesc();
}
