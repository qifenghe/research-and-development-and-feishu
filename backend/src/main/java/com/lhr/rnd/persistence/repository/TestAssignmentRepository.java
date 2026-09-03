package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TestAssignmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestAssignmentRepository extends JpaRepository<TestAssignmentEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from TestAssignmentEntity assignment where assignment.id = :id")
    Optional<TestAssignmentEntity> findByIdForUpdate(@Param("id") String id);

    List<TestAssignmentEntity> findByTaskIdOrderByAssignedAtDesc(String taskId);

    Optional<TestAssignmentEntity> findFirstByTaskIdAndArchivedAtIsNullOrderByAssignedAtDesc(String taskId);
}
