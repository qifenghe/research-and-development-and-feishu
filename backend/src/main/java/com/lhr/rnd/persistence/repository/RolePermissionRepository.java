package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, String> {
    long countByRoleCode(String roleCode);

    List<RolePermissionEntity> findByRoleCodeAndEnabledTrueOrderBySortOrderAsc(String roleCode);

    List<RolePermissionEntity> findByRoleCodeOrderBySortOrderAsc(String roleCode);

    void deleteByRoleCode(String roleCode);
}
