package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TestRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TestRecordRepository extends JpaRepository<TestRecordEntity, String> {
    List<TestRecordEntity> findByTestAssignmentIdInOrderByTestedAtDesc(Collection<String> testAssignmentIds);
}
