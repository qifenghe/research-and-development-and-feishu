package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.ShipmentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRecordRepository extends JpaRepository<ShipmentRecordEntity, String> {
}
