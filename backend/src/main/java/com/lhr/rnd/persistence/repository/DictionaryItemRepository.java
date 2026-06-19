package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.DictionaryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String> {
    long countByCategory(String category);

    List<DictionaryItemEntity> findByCategoryOrderBySortOrderAsc(String category);

    List<DictionaryItemEntity> findAllByOrderByCategoryAscSortOrderAsc();

    void deleteByCategory(String category);
}
