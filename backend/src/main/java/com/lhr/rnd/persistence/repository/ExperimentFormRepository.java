package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentFormRepository extends JpaRepository<ExperimentFormEntity, String> {
}
