package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.PricingPackagingItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingPackagingItemRepository extends JpaRepository<PricingPackagingItemEntity, String> {
    List<PricingPackagingItemEntity> findByPricingFileIdOrderBySequenceAsc(String pricingFileId);

    void deleteByPricingFileId(String pricingFileId);
}
