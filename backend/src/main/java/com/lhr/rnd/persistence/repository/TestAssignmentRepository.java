package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TestAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestAssignmentRepository extends JpaRepository<TestAssignmentEntity, String> {
    List<TestAssignmentEntity> findByTaskIdOrderByAssignedAtDesc(String taskId);
}
