package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.PricingFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PricingFileRepository extends JpaRepository<PricingFileEntity, String> {
    boolean existsByVersionId(String versionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pricingFile from PricingFileEntity pricingFile where pricingFile.id = :id")
    Optional<PricingFileEntity> findByIdForUpdate(@Param("id") String id);
}
