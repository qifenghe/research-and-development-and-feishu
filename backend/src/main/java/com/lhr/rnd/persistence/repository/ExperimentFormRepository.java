package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ExperimentFormRepository extends JpaRepository<ExperimentFormEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select form from ExperimentFormEntity form where form.id = :id")
    Optional<ExperimentFormEntity> findByIdForUpdate(@Param("id") String id);

    Optional<ExperimentFormEntity> findFirstByTaskIdOrderBySavedAtDesc(String taskId);

    Optional<ExperimentFormEntity> findFirstByTaskIdAndVersionIdOrderBySavedAtDesc(String taskId, String versionId);

    Optional<ExperimentFormEntity> findFirstByVersionIdAndStatusOrderBySavedAtDesc(String versionId, String status);

    List<ExperimentFormEntity> findByStatus(String status);
}
