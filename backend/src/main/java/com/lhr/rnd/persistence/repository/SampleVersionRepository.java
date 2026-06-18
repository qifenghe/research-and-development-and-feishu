package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.SampleVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleVersionRepository extends JpaRepository<SampleVersionEntity, String> {
}
