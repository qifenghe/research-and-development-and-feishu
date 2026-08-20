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
    void exposesOnlyRevisionAwareProcessEndpointsForEachBusinessRole() {
        var draft = "/api/v1/experiment-forms/FORM-1/process-plan";
        var submissionCheck = draft + "/submission-check";
        var submit = draft + "/submit";
        var revisions = draft + "/revisions";
        var revision = revisions + "/REV-1";
        var newDraft = revision + "/new-draft";
        var artifacts = revision + "/artifacts";
        var download = artifacts + "/ART-1/download";

        for (var role : new String[]{"RND_ENGINEER", "RND_DIRECTOR"}) {
            assertThat(service.hasPermission(role, "GET", draft)).isTrue();
            assertThat(service.hasPermission(role, "GET", submissionCheck)).isTrue();
            assertThat(service.hasPermission(role, "PUT", draft)).isTrue();
            assertThat(service.hasPermission(role, "POST", submit)).isTrue();
            assertThat(service.hasPermission(role, "GET", revisions)).isTrue();
            assertThat(service.hasPermission(role, "GET", revision)).isTrue();
            assertThat(service.hasPermission(role, "POST", newDraft)).isTrue();
            assertThat(service.hasPermission(role, "GET", artifacts)).isTrue();
            assertThat(service.hasPermission(role, "POST", artifacts)).isTrue();
            assertThat(service.hasPermission(role, "GET", download)).isTrue();
        }

        for (var role : new String[]{"TESTER", "QA_TESTER"}) {
            assertThat(service.hasPermission(role, "GET", draft)).isFalse();
            assertThat(service.hasPermission(role, "GET", submissionCheck)).isFalse();
            assertThat(service.hasPermission(role, "PUT", draft)).isFalse();
            assertThat(service.hasPermission(role, "POST", submit)).isFalse();
            assertThat(service.hasPermission(role, "GET", revisions)).isTrue();
            assertThat(service.hasPermission(role, "GET", revision)).isTrue();
            assertThat(service.hasPermission(role, "POST", newDraft)).isFalse();
            assertThat(service.hasPermission(role, "GET", artifacts)).isTrue();
            assertThat(service.hasPermission(role, "POST", artifacts)).isFalse();
            assertThat(service.hasPermission(role, "GET", download)).isTrue();
        }

        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/pricing-files/PRICE-1/detail")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", draft)).isFalse();
        assertThat(service.hasPermission("FINANCE", "GET", revision)).isFalse();
        assertThat(service.hasPermission("FINANCE", "PUT", draft)).isFalse();
        assertThat(service.hasPermission("FINANCE", "POST", submit)).isFalse();
        assertThat(service.hasPermission("FINANCE", "POST", artifacts)).isFalse();

        for (var role : new String[]{"RND_ENGINEER", "RND_DIRECTOR", "TESTER", "QA_TESTER", "FINANCE"}) {
            assertThat(service.rolePermissions(role).permissions())
                    .noneMatch(rule -> rule.pathPattern().equals("/api/v1/experiment-forms/*/process-plan/**"));
        }
    }

    @Test
    void fallsBackToDefaultPermissionMatrixWhenRoleHasNoConfiguredRules() {
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/sample-requests")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/sample-requests")).isFalse();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/sample-projects/stopped")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/sample-projects/stopped")).isFalse();
        assertThat(service.hasPermission("MANAGER", "GET", "/api/v1/dashboard/overview")).isTrue();
        assertThat(service.hasPermission("TESTER", "GET", "/api/v1/dashboard/overview")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/rnd-tasks")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/rnd-tasks/TASK-0001/detail")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/rnd-tasks/TASK-0001/detail")).isTrue();
        assertThat(service.hasPermission("TESTER", "GET", "/api/v1/rnd-tasks/TASK-0001/detail")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/rnd-tasks/TASK-0001/detail")).isFalse();
        assertThat(service.hasPermission("RND_ASSISTANT", "GET", "/api/v1/pricing-files")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/pricing-files")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/pricing-files")).isTrue();
        assertThat(service.hasPermission("RND_ASSISTANT", "GET", "/api/v1/shipments/SHIP-0001/detail")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/shipments/SHIP-0001/detail")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/shipments/SHIP-0001/detail")).isFalse();
        assertThat(service.hasPermission("RND_ASSISTANT", "GET", "/api/v1/pricing-files/PRICE-0001/detail")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/pricing-files/PRICE-0001/detail")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/pricing-files/PRICE-0001/detail")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/pricing-files/PRICE-0001/detail")).isTrue();
        assertThat(service.hasPermission("RND_ASSISTANT", "GET", "/api/v1/pricing-files/PRICE-0001/download")).isTrue();
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/experiment-forms/EXP-0001/submit-test")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/experiment-forms/EXP-0001/submit-test")).isTrue();
        assertThat(service.hasPermission("TESTER", "POST", "/api/v1/experiment-forms/EXP-0001/submit-test")).isFalse();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/pricing-files/PRICE-0001/download")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/pricing-files/PRICE-0001/download")).isTrue();
        assertThat(service.hasPermission("FINANCE", "POST", "/api/v1/pricing-files/PRICE-0001/receive")).isTrue();
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/pricing-files/PRICE-0001/receive")).isFalse();
        assertThat(service.hasPermission("RND_ASSISTANT", "GET", "/api/v1/sample-versions/pricing-ready")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/sample-versions/pricing-ready")).isFalse();
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/pricing-files/PRICE-0001/notify-finance")).isTrue();
        assertThat(service.hasPermission("FINANCE", "POST", "/api/v1/pricing-files/PRICE-0001/notify-finance")).isFalse();
        assertThat(service.hasPermission("RND_DIRECTOR", "POST", "/api/v1/pricing-files/PRICE-0001/review")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/pricing-files/PRICE-0001/review")).isTrue();
        assertThat(service.hasPermission("RND_ASSISTANT", "POST", "/api/v1/pricing-files/PRICE-0001/review")).isFalse();
        assertThat(service.hasPermission("FINANCE", "POST", "/api/v1/pricing-files/PRICE-0001/review")).isFalse();
        assertThat(service.hasPermission("TESTER", "GET", "/api/v1/pricing-files/PRICE-0001/download")).isFalse();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/sample-projects/PROJ-1/version-timeline")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/sample-versions/VER-1/process-steps")).isTrue();
        assertThat(service.hasPermission("FINANCE", "GET", "/api/v1/sample-projects/PROJ-1/version-timeline")).isFalse();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/sample-requests/REQ-1")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "POST", "/api/v1/rnd-tasks/TASK-0001/accept")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "POST", "/api/v1/rnd-tasks/TASK-0001/experiment-form/draft")).isTrue();
        assertThat(service.hasPermission("RND_DIRECTOR", "GET", "/api/v1/experiment-forms/FORM-1/process-plan/submission-check")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/experiment-forms/FORM-1/process-plan/submission-check")).isTrue();
        assertThat(service.hasPermission("TESTER", "GET", "/api/v1/experiment-forms/FORM-1/process-plan/submission-check")).isFalse();
        assertThat(service.hasPermission("TESTER", "PUT", "/api/v1/experiment-forms/FORM-1/process-plan")).isFalse();
        assertThat(service.hasPermission("RND_ENGINEER", "POST", "/api/v1/experiment-forms/FORM-1/process-plan/revisions/PREV-1/artifacts")).isTrue();
        assertThat(service.hasPermission("RND_ENGINEER", "GET", "/api/v1/experiment-forms/FORM-1/process-plan/revisions/PREV-1/artifacts/PART-1/download")).isTrue();
        assertThat(service.hasPermission("TESTER", "GET", "/api/v1/experiment-forms/FORM-1/process-plan/revisions/PREV-1/artifacts")).isTrue();
        assertThat(service.hasPermission("TESTER", "POST", "/api/v1/experiment-forms/FORM-1/process-plan/revisions/PREV-1/artifacts")).isFalse();
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
