package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.RndTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RndTaskRepository extends JpaRepository<RndTaskEntity, String> {
}
