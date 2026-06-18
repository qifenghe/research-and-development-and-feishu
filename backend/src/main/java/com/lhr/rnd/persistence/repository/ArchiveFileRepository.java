package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ArchiveFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveFileRepository extends JpaRepository<ArchiveFileEntity, String> {
}
