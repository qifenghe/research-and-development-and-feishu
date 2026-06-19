package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ArchiveFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveFileRepository extends JpaRepository<ArchiveFileEntity, String> {
    List<ArchiveFileEntity> findByVersionIdOrderByArchivedAtDesc(String versionId);
}
