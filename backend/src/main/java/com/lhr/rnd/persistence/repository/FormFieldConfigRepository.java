package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.FormFieldConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormFieldConfigRepository extends JpaRepository<FormFieldConfigEntity, String> {
    long countByFormCode(String formCode);

    List<FormFieldConfigEntity> findByFormCodeOrderBySortOrderAsc(String formCode);

    List<FormFieldConfigEntity> findAllByOrderByFormCodeAscSortOrderAsc();

    void deleteByFormCode(String formCode);
}
