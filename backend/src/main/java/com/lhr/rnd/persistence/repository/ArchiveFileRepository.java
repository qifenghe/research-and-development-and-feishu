package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ArchiveFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArchiveFileRepository extends JpaRepository<ArchiveFileEntity, String> {
    List<ArchiveFileEntity> findByVersionIdOrderByArchivedAtDesc(String versionId);

    Optional<ArchiveFileEntity> findFirstByBusinessTypeAndBusinessIdOrderByArchivedAtDesc(String businessType, String businessId);
}
