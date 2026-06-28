package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.TemplateConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateConfigRepository extends JpaRepository<TemplateConfigEntity, String> {
    List<TemplateConfigEntity> findByTemplateTypeOrderByUpdatedAtDesc(String templateType);
}
