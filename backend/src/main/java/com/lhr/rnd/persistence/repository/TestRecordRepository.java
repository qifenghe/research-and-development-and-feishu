package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TestRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRecordRepository extends JpaRepository<TestRecordEntity, String> {
}
