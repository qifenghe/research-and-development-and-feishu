package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentProcessRepository extends JpaRepository<ExperimentProcessEntity, String> {
    List<ExperimentProcessEntity> findByExperimentFormIdOrderBySequenceAsc(String experimentFormId);

    void deleteByExperimentFormId(String experimentFormId);
}
