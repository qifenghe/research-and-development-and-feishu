package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentMaterialRepository extends JpaRepository<ExperimentMaterialEntity, String> {
    List<ExperimentMaterialEntity> findByExperimentFormIdOrderBySequenceAsc(String experimentFormId);

    void deleteByExperimentFormId(String experimentFormId);
}
