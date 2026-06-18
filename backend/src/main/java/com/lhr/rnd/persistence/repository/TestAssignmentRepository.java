package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TestAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAssignmentRepository extends JpaRepository<TestAssignmentEntity, String> {
}
