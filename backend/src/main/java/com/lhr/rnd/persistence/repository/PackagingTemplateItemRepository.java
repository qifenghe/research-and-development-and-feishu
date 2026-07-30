package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.PackagingTemplateItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackagingTemplateItemRepository extends JpaRepository<PackagingTemplateItemEntity, String> {
    List<PackagingTemplateItemEntity> findByTemplateCodeAndEnabledTrueOrderBySequenceAsc(String templateCode);
}
