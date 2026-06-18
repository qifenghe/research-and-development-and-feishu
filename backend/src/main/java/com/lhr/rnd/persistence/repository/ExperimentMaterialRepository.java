package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentMaterialRepository extends JpaRepository<ExperimentMaterialEntity, String> {
    void deleteByExperimentFormId(String experimentFormId);
}
