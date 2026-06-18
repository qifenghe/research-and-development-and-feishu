package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.SampleProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleProjectRepository extends JpaRepository<SampleProjectEntity, String> {
}
