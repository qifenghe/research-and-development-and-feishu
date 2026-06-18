package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.PricingFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingFileRepository extends JpaRepository<PricingFileEntity, String> {
}
