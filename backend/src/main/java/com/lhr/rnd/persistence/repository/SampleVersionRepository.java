package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.SampleVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SampleVersionRepository extends JpaRepository<SampleVersionEntity, String> {
    List<SampleVersionEntity> findByProjectIdOrderByVersionNumberDesc(String projectId);
}
