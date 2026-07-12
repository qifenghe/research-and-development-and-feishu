package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ExperimentFormRepository extends JpaRepository<ExperimentFormEntity, String> {
    Optional<ExperimentFormEntity> findFirstByTaskIdOrderBySavedAtDesc(String taskId);

    Optional<ExperimentFormEntity> findFirstByTaskIdAndVersionIdOrderBySavedAtDesc(String taskId, String versionId);

    Optional<ExperimentFormEntity> findFirstByVersionIdAndStatusOrderBySavedAtDesc(String versionId, String status);

    List<ExperimentFormEntity> findByStatus(String status);
}
