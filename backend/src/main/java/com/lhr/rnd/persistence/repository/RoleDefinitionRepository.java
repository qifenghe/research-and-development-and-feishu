package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.RoleDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinitionEntity, String> {
    List<RoleDefinitionEntity> findAllByOrderBySystemBuiltinDescRoleCodeAsc();

    boolean existsByRoleName(String roleName);
}
