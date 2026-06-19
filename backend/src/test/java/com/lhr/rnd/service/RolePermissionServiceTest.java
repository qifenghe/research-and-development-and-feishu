package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.RolePermissionEntity;
import com.lhr.rnd.persistence.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(RolePermissionService.class)
@ActiveProfiles("test")
class RolePermissionServiceTest {

    @Autowired
    private RolePermissionRepository repository;

    @Autowired
    private RolePermissionService service;

    @Test
    void fallsBackToDefaultPermissionMatrixWhenRoleHasNoConfiguredRules() {
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/sample-requests")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/sample-requests")).isFalse();
    }

    @Test
    void usesConfiguredRulesForRoleWhenRulesExist() {
        repository.save(new RolePermissionEntity(
                "PERM-9001",
                "RND_ENGINEER",
                "POST",
                "/api/v1/sample-requests",
                true,
                "临时允许研发人员录入样品需求",
                10,
                LocalDateTime.of(2026, 6, 19, 23, 20)
        ));

        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/sample-requests")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/sample-requests/REQ-1/approve")).isFalse();
    }
}
