package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExperimentFormRepository extends JpaRepository<ExperimentFormEntity, String> {
    Optional<ExperimentFormEntity> findFirstByTaskIdOrderBySavedAtDesc(String taskId);
}
