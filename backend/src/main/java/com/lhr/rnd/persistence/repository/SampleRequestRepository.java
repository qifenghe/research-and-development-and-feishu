package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.SampleRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SampleRequestRepository extends JpaRepository<SampleRequestEntity, String> {
    Optional<SampleRequestEntity> findBySampleNo(String sampleNo);
}
