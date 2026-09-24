package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.TrialScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TrialPromotionServiceTest {
    @Autowired TrialPromotionService promotion;
    @Autowired TrialSchemeService trials;
    @Autowired ProcessPlanService plans;
    @Autowired ProcessRevisionService revisions;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RolePermissionService permissions;
    @Autowired ProcessArtifactService artifacts;

    @Test void inheritedExportProvenanceComesFromPromotionSnapshotNotMutableTrial() throws Exception {
        var source = create();
        var copy = trials.copy(form, source.id(), new TrialSchemeService.CopyCommand(source.versionNo(), "继承方案B", true), owner);
        var changed = save(copy, edit(copy.plan(), node -> ((ObjectNode)control(node).path("measurements").get(0)).put("measuredAt", "2026-09-15T10:01:00")));
        var confirmed = confirm(changed);
        assertThat(artifacts.trialView(form, confirmed.id(), confirmed.versionNo(), owner).sourceLabel()).contains("含继承实测");
        var revision = promotion.submit(form, confirmed.id(), command(confirmed, preview(confirmed), true, "inherited-export"), owner);
        var before = artifacts.revisionView(form, revision.id(), owner);
        assertThat(before.sourceLabel()).contains("含继承实测");
        jdbc.update("update experiment_trial_scheme set inherited_actuals=false where id=?", confirmed.id());
        assertThat(artifacts.revisionView(form, revision.id(), owner).sourceLabel()).isEqualTo(before.sourceLabel());
        assertThat(mapper.writeValueAsString(promotion.source(form, revision.id(), owner))).doesNotContain("plannedData", "qualityNotes", "purpose");
        for (var role : List.of("TESTER", "FINANCE")) {
            var denied = new SessionPrincipal("other", "other", "其他", null, role, null);
            assertThatThrownBy(() -> artifacts.revisionView(form, revision.id(), denied)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> artifacts.trialView(form, confirmed.id(), confirmed.versionNo(), denied)).isInstanceOf(BusinessException.class);
        }
    }
    String form;
    SessionPrincipal owner;
    final SessionPrincipal director = new SessionPrincipal("PROMO-DIR", "promo-dir", "总监", null, "RND_DIRECTOR", null);
    final SessionPrincipal tester = new SessionPrincipal("PROMO-TEST", "promo-test", "测试", null, "TESTER", null);

    @BeforeEach void seed() {
        var key = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        form = "F-" + key;
        owner = new SessionPrincipal("U-" + key, "u" + key, "研发", null, "RND_ENGINEER", null);
        var now = LocalDateTime.now();
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,'ACTIVE',?,?)", owner.userId(), owner.username(), "x", owner.name(), owner.role(), now, now);
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)", "R-"+key, key, "牛腩", "预制菜", "客户", "1kg", "研发", "PENDING_ASSIGNMENT", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)", "P-"+key, "R-"+key, key, "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)", "V-"+key, "P-"+key, key, "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,assignee_name,assignee_user_id,created_at) values (?,?,?,?,?,?,?,?,?,?)", "T-"+key, "P-"+key, "V-"+key, key, "牛腩", "V1", "SAMPLING", owner.name(), owner.userId(), now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)", form, "T-"+key, "P-"+key, "V-"+key, key, "牛腩", "V1", "DRAFT", owner.name(), now);
    }

    @Test void rejectsUnconfirmedStaleUnauthorizedAndChangedPreviewWithoutMutation() {
        var trial = create();
        var initial = plans.find(form);
        var preview = promotion.preview(form, trial.id(), new TrialPromotionService.PreviewCommand(1, initial.versionNo()), owner);
        assertThat(preview.checks().ready()).isFalse();
        assertThat(preview.checks().errors()).extracting(e -> e.code()).contains("CRITICAL_CONTROL_UNRESOLVED");
        code(() -> promotion.submit(form, trial.id(), command(trial, preview, false, "key"), owner), "PROCESS_SUBMISSION_CONFIRMATION_REQUIRED");
        code(() -> promotion.submit(form, trial.id(), command(trial, preview, true, "key"), owner), "PROCESS_PLAN_NOT_READY");
        code(() -> promotion.preview(form, trial.id(), new TrialPromotionService.PreviewCommand(2, initial.versionNo()), owner), "TRIAL_VERSION_CONFLICT");
        code(() -> promotion.preview(form, trial.id(), new TrialPromotionService.PreviewCommand(1, initial.versionNo()), tester), "PROCESS_PLAN_FORM_FORBIDDEN");
        var confirmed = confirm(trial);
        code(() -> promotion.submit(form, confirmed.id(), new TrialPromotionService.SubmitCommand(confirmed.versionNo(), initial.versionNo(), preview.previewToken(), true, null, "key"), owner), "TRIAL_PREVIEW_STALE");
        assertThat(plans.find(form)).usingRecursiveComparison().isEqualTo(initial);
    }

    @Test void savesTrustedConfirmationOnlyWhileLocationDefinitionAndObservationsAreUnchanged() {
        var confirmed = confirm(create());
        assertThat(point(confirmed).confirmedBy()).isEqualTo(owner.name());
        var unchanged = save(confirmed, confirmed.plan());
        assertThat(point(unchanged).confirmedAt()).isEqualTo(point(confirmed).confirmedAt());
        var spoof = edit(unchanged.plan(), node -> control(node).put("confirmedBy", "伪造总监"));
        var sanitized = save(unchanged, spoof);
        assertThat(point(sanitized).confirmedBy()).isEqualTo(owner.name());
        var changed = save(sanitized, edit(sanitized.plan(), node -> control(node).put("upperLimit", 90)));
        assertThat(point(changed).confirmedBy()).isNull();
        assertThat(point(changed).resolved()).isFalse();
        var reconfirmed = confirm(changed);
        var observed = save(reconfirmed, edit(reconfirmed.plan(), node -> ((ObjectNode)control(node).path("measurements").get(0)).put("measuredValue", 77)));
        assertThat(point(observed).confirmedBy()).isNull();
        var finalConfirmed = confirm(observed);
        var moved = save(finalConfirmed, edit(finalConfirmed.plan(), node -> ((ObjectNode)node.path("majorProcesses").get(0).path("steps").get(0)).put("id", "new-step")));
        assertThat(point(moved).confirmedBy()).isNull();
    }

    @Test void copiedObservationsCannotBeReconfirmedByRekeyingResultOrTimeFormatting() {
        var source = confirm(create());
        var copied = trials.copy(form, source.id(), new TrialSchemeService.CopyCommand(source.versionNo(), "继承", true), owner);
        var saved = save(copied, edit(copied.plan(), node -> {
            var measurement = (ObjectNode)control(node).path("measurements").get(0);
            measurement.put("id", "new-measurement");
            measurement.put("measuredAt", "2026-09-15T10:00:00.000");
            measurement.put("result", "FAIL");
        }));
        code(() -> confirm(saved), "TRIAL_INHERITED_MEASUREMENT");
        var changed = save(saved, edit(saved.plan(), node -> ((ObjectNode)control(node).path("measurements").get(0)).put("measuredAt", "2026-09-15T10:00:01").put("result", "PASS")));
        assertThat(point(confirm(changed)).confirmedBy()).isEqualTo(owner.name());
    }

    @Test void directorDeviationClosureRequiresDocumentedDispositionAndFreshPassingRetest() {
        var trial = create();
        var failed = save(trial, edit(trial.plan(), node -> {
            control(node).put("basisOrRemark", "标准依据 GB-内部试验规范");
            var measurement = (ObjectNode)control(node).path("measurements").get(0);
            measurement.put("result", "FAIL").put("measuredValue", 65).put("deviationAction", "延长熟制");
        }));
        code(() -> confirm(failed), "TRIAL_CONTROL_NOT_PASSING");
        code(() -> trials.confirmDeviation(form, failed.id(), point(failed).id(), new TrialSchemeService.ConfirmDeviationCommand(failed.versionNo(), "处理完成"), owner), "TRIAL_DEVIATION_CONFIRMATION_FORBIDDEN");
        code(() -> trials.confirmDeviation(form, failed.id(), point(failed).id(), new TrialSchemeService.ConfirmDeviationCommand(failed.versionNo(), "处理完成"), director), "TRIAL_DEVIATION_NOT_RESOLVED");
        var retested = save(failed, edit(failed.plan(), node -> {
            var ms = (com.fasterxml.jackson.databind.node.ArrayNode)control(node).path("measurements");
            var retest = ms.get(0).deepCopy();
            ((ObjectNode)retest).put("id", "retest").put("sequence", 2).put("measuredAt", "2026-09-15T10:05").put("measuredValue", 76).put("result", "PASS");
            ms.add(retest);
        }));
        var confirmed = trials.confirmDeviation(form, retested.id(), point(retested).id(), new TrialSchemeService.ConfirmDeviationCommand(retested.versionNo(), "延长熟制后复测通过"), director);
        assertThat(point(confirmed).resolved()).isTrue();
        assertThat(point(confirmed).confirmedBy()).isEqualTo(director.name());
        assertThat(point(confirmed).basisOrRemark()).isEqualTo("标准依据 GB-内部试验规范");
        assertThat(point(confirmed).measurements().get(0).retestResult()).isEqualTo("PASS");
        var unchanged = save(confirmed, confirmed.plan());
        assertThat(point(unchanged).resolved()).isTrue();
        var preview = preview(unchanged);
        assertThat(preview.checks().ready()).isTrue();
        var revision = promotion.submit(form, unchanged.id(), command(unchanged, preview, true, "deviation"), owner);
        assertThat(revision.snapshot().majorProcesses().get(0).steps().get(0).controlPoints().get(0).resolved()).isTrue();
    }

    @Test void preservesDisplacedDraftAndImmutableTrialSnapshotAndRetriesExactlyOnce() throws Exception {
        var draft = plans.save(form, new ProcessPlan(null, form, plans.find(form).versionNo(), "DRAFT", List.of(), null, false));
        var initialTrial = create();
        var planned = new TrialScheme.PlannedData(java.util.Map.of(initialTrial.plan().majorProcesses().get(0).steps().get(0).materials().get(0).id(), new BigDecimal("11")), java.util.Map.of(), java.util.Map.of(), new BigDecimal("99"), "按实际主料链核算");
        var evaluated = trials.save(form, initialTrial.id(), new TrialSchemeService.SaveCommand(initialTrial.versionNo(), initialTrial.name(), initialTrial.purpose(), initialTrial.variables(), TrialScheme.Conclusion.RECOMMEND, "品质稳定", new BigDecimal("9"), "已验证", TrialScheme.Difficulty.EASY, initialTrial.plan(), planned), owner);
        var trial = confirm(evaluated);
        var preview = preview(trial);
        assertThat(preview.differingDraft()).isTrue();
        var request = command(trial, preview, true, "once");
        var first = promotion.submit(form, trial.id(), request, owner);
        var retry = promotion.submit(form, trial.id(), request, owner);
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(first.snapshot().majorProcesses().get(0).id()).isNotEqualTo(trial.plan().majorProcesses().get(0).id());
        assertThat(first.snapshot().majorProcesses().get(0).steps().get(0).controlPoints().get(0).confirmedBy()).isEqualTo(owner.name());
        assertThat(promotion.displacedDraft(form, first.id(), owner)).usingRecursiveComparison().isEqualTo(draft);
        code(() -> promotion.displacedDraft(form, first.id(), tester), "PROCESS_PLAN_FORM_FORBIDDEN");
        code(() -> promotion.submit(form, trial.id(), new TrialPromotionService.SubmitCommand(request.trialVersionNo(), request.expectedProcessVersionNo(), request.previewToken(), true, "different", "once"), owner), "TRIAL_IDEMPOTENCY_CONFLICT");
        var jsonBefore = jdbc.queryForObject("select trial_snapshot_json from experiment_trial_promotion where revision_id=?", String.class, first.id());
        save(trial, edit(trial.plan(), node -> control(node).put("itemName", "后来修改")));
        assertThat(jdbc.queryForObject("select trial_snapshot_json from experiment_trial_promotion where revision_id=?", String.class, first.id())).isEqualTo(jsonBefore);
        assertThat(mapper.readValue(jsonBefore, TrialScheme.class).name()).isEqualTo("方案A");
        assertThat(mapper.readValue(jsonBefore, TrialScheme.class).plannedData().batchYieldTarget()).isEqualByComparingTo("99");
        assertThat(mapper.readValue(jsonBefore, TrialScheme.class).qualityScore()).isEqualByComparingTo("9");
        assertThat(first.snapshot().batchYieldPercent()).isEqualByComparingTo("100");
        assertThat(revisions.find(form, first.id()).snapshotHash()).isEqualTo(first.snapshotHash());
        var metadata = promotion.source(form, first.id(), owner);
        assertThat(metadata.trialName()).isEqualTo("方案A");
        assertThat(metadata.trialVersionNo()).isEqualTo(trial.versionNo());
        assertThat(mapper.writeValueAsString(metadata)).doesNotContain("plannedData", "qualityNotes", "plan", "purpose");
    }

    @Test void submittedFormalTransitionRequiresReasonAndKeepsPreviousSnapshotImmutable() {
        var firstTrial = confirm(create());
        var first = promotion.submit(form, firstTrial.id(), command(firstTrial, preview(firstTrial), true, "first"), owner);
        var secondTrial = confirm(create());
        var preview = preview(secondTrial);
        assertThat(preview.differingDraft()).isFalse();
        code(() -> promotion.submit(form, secondTrial.id(), command(secondTrial, preview, true, "second"), owner), "PROCESS_CHANGE_REASON_REQUIRED");
        var second = promotion.submit(form, secondTrial.id(), new TrialPromotionService.SubmitCommand(secondTrial.versionNo(), preview.expectedProcessVersionNo(), preview.previewToken(), true, "验证新方案", "second"), owner);
        assertThat(second.revisionNo()).isEqualTo(2);
        assertThat(second.sourceRevisionId()).isEqualTo(first.id());
        assertThat(second.changeReason()).isEqualTo("验证新方案");
        assertThat(revisions.find(form, first.id())).usingRecursiveComparison().isEqualTo(first);
        assertThat(promotion.displacedDraft(form, second.id(), owner)).isNull();
    }

    @Test void semanticallyIdenticalRemappedDraftIsNotReportedAsDifferent() {
        var trial = confirm(create());
        var plan = trial.plan();
        plans.save(form, new ProcessPlan(null, form, plans.find(form).versionNo(), "DRAFT", plan.majorProcesses(), null, false));
        assertThat(preview(trial).differingDraft()).isFalse();
    }

    @Test void invalidFormalGraphRollsBackDraftAndPromotionRows() {
        var before = populatedDraft();
        var trial = confirm(create());
        // The formal storage validates names; preview uses the existing formal submission validator.
        var invalid = save(trial, edit(trial.plan(), node -> ((ObjectNode)node.path("majorProcesses").get(0)).put("processName", "")));
        var preview = preview(invalid);
        assertThatThrownBy(() -> promotion.submit(form, invalid.id(), command(invalid, preview, true, "rollback"), owner)).isInstanceOf(BusinessException.class);
        assertThat(plans.find(form)).usingRecursiveComparison().isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from experiment_trial_promotion where experiment_form_id=?", Integer.class, form)).isZero();
        assertThat(revisions.list(form)).isEmpty();
    }

    @Test void promotionInsertFailureAfterRevisionCreationRollsBackPopulatedGraphAndAudit() {
        // A database constraint fails only at the final promotion insert, after graph/revision/audit writes.
        var before = populatedDraft();
        var trial = confirm(create());
        var preview = preview(trial);
        var auditBefore = jdbc.queryForList("select * from audit_log order by id");
        var constraint = "reject_promotion_" + form.substring(2);
        jdbc.execute("alter table experiment_trial_promotion add constraint " + constraint
                + " check (experiment_form_id <> '" + form + "')");
        try {
            assertThatThrownBy(() -> promotion.submit(form, trial.id(), command(trial, preview, true, "late-rollback"), owner))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(plans.find(form)).usingRecursiveComparison().isEqualTo(before);
            assertThat(revisions.list(form)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from experiment_trial_promotion where experiment_form_id=?", Integer.class, form)).isZero();
            assertThat(jdbc.queryForList("select * from audit_log order by id")).isEqualTo(auditBefore);
            assertThat(trials.find(form, trial.id(), owner)).usingRecursiveComparison().isEqualTo(trial);
        } finally {
            jdbc.execute("alter table experiment_trial_promotion drop constraint " + constraint);
        }
        // The earlier preview survives; removing the fault permits exactly the original request.
        var revision = promotion.submit(form, trial.id(), command(trial, preview, true, "late-rollback"), owner);
        assertThat(promotion.displacedDraft(form, revision.id(), owner)).usingRecursiveComparison().isEqualTo(before);
    }

    private ProcessPlan populatedDraft() {
        var graph = edit(graph(), node -> {
            var major = (ObjectNode) node.path("majorProcesses").get(0);
            major.put("processName", "保留的完整草稿");
            var steps = (com.fasterxml.jackson.databind.node.ArrayNode) major.path("steps");
            var first = (ObjectNode) steps.get(0);
            ((ObjectNode) first.path("outputs").get(0)).put("outputType", "INTERMEDIATE").put("continueFlow", true);
            var second = first.deepCopy();
            second.put("id", "step-two").put("sequence", 2).put("stepCode", "PACK").put("stepName", "包装");
            ((ObjectNode) second.path("materials").get(0)).put("id", "mat-two").put("sourceType", "STEP_OUTPUT").put("sourceStepOutputId", "out").putNull("materialCode");
            ((ObjectNode) second.path("outputs").get(0)).put("id", "out-two").put("outputType", "FINISHED").put("continueFlow", false);
            second.putArray("controlPoints");
            steps.add(second);
            ((com.fasterxml.jackson.databind.node.ArrayNode) major.path("inputs")).add(mapper.createObjectNode()
                    .put("id", "input").put("sequence", 1).put("inputRole", "PRIMARY").put("materialCode", "BEEF")
                    .put("materialName", "牛腩").put("weightKg", 10).put("sourceStepMaterialId", "mat"));
        });
        var mapped = new com.lhr.rnd.domain.TrialSchemeCopyService().normalizeNew(graph, TrialScheme.PlannedData.empty()).plan();
        var saved = plans.savePromotedTrial(form, new ProcessPlan(null, form, plans.find(form).versionNo(), "DRAFT", mapped.majorProcesses(), null, false));
        var major = saved.majorProcesses().get(0);
        assertThat(major.inputs().get(0).sourceStepMaterialId()).isEqualTo(major.steps().get(0).materials().get(0).id());
        assertThat(major.steps().get(1).materials().get(0).sourceStepOutputId()).isEqualTo(major.steps().get(0).outputs().get(0).id());
        return saved;
    }

    @Test void actualGraphReferencesSurviveFormalMaterialIdAllocation() {
        var trial = create();
        var withInput = save(trial, edit(trial.plan(), node -> {
            var major = (ObjectNode)node.path("majorProcesses").get(0);
            var input = mapper.createObjectNode().put("id", "input").put("sequence", 1).put("inputRole", "PRIMARY").put("materialCode", "BEEF").put("materialName", "牛腩").put("weightKg", 10);
            input.put("sourceStepMaterialId", major.path("steps").get(0).path("materials").get(0).path("id").asText());
            ((com.fasterxml.jackson.databind.node.ArrayNode)major.path("inputs")).add(input);
        }));
        var confirmed = confirm(withInput);
        var revision = promotion.submit(form, confirmed.id(), command(confirmed, preview(confirmed), true, "refs"), owner);
        var major = revision.snapshot().majorProcesses().get(0);
        assertThat(major.inputs().get(0).sourceStepMaterialId()).isEqualTo(major.steps().get(0).materials().get(0).id());
    }

    @Test void exposesOnlyExactRoleAppropriateRoutes() {
        var base = "/api/v1/experiment-forms/F/trials/T";
        for (var role : List.of("RND_ENGINEER", "RND_DIRECTOR")) {
            assertThat(permissions.hasPermission(role, "POST", base + "/submission-preview")).isTrue();
            assertThat(permissions.hasPermission(role, "POST", base + "/submit")).isTrue();
            assertThat(permissions.hasPermission(role, "POST", base + "/control-points/P/confirm")).isTrue();
            assertThat(permissions.hasPermission(role, "GET", "/api/v1/experiment-forms/F/process-plan/revisions/R/displaced-draft")).isTrue();
        }
        assertThat(permissions.hasPermission("RND_DIRECTOR", "POST", base + "/control-points/P/confirm-deviation")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "POST", base + "/control-points/P/confirm-deviation")).isFalse();
        for (var role : List.of("TESTER", "QA_TESTER")) {
            assertThat(permissions.hasPermission(role, "GET", "/api/v1/experiment-forms/F/process-plan/revisions/R/source")).isTrue();
            assertThat(permissions.hasPermission(role, "GET", "/api/v1/experiment-forms/F/process-plan/revisions/R/displaced-draft")).isFalse();
            assertThat(permissions.hasPermission(role, "POST", base + "/submit")).isFalse();
        }
    }

    @Test void sourceUsesPinnedTesterRevisionAndNeverGrantsDraftAccess() {
        var trial = confirm(create());
        var first = promotion.submit(form, trial.id(), command(trial, preview(trial), true, "one"), owner);
        var next = confirm(create());
        var preview = preview(next);
        var second = promotion.submit(form, next.id(), new TrialPromotionService.SubmitCommand(next.versionNo(), preview.expectedProcessVersionNo(), preview.previewToken(), true, "新版本", "two"), owner);
        code(() -> promotion.source(form, first.id(), tester), "PROCESS_REVISION_NOT_ASSIGNED");
        if (jdbc.queryForObject("select count(*) from user_account where id=?", Integer.class, tester.userId()) == 0) {
            jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,'ACTIVE',current_timestamp,current_timestamp)", tester.userId(), tester.username(), "x", tester.name(), tester.role());
        }
        var task = jdbc.queryForObject("select task_id from experiment_form where id=?", String.class, form);
        var version = jdbc.queryForObject("select version_id from experiment_form where id=?", String.class, form);
        jdbc.update("insert into test_assignment(id,experiment_form_id,task_id,version_id,process_revision_id,tester_name,tester_user_id,status,assigned_at) values (?,?,?,?,?,?,?,?,current_timestamp)", "A-" + form.substring(2), form, task, version, first.id(), tester.name(), tester.userId(), "PENDING");
        assertThat(promotion.source(form, first.id(), tester).trialId()).isEqualTo(trial.id());
        code(() -> promotion.source(form, second.id(), tester), "PROCESS_REVISION_NOT_ASSIGNED");
        code(() -> promotion.displacedDraft(form, first.id(), tester), "PROCESS_PLAN_FORM_FORBIDDEN");
        code(() -> trials.find(form, trial.id(), tester), "PROCESS_PLAN_FORM_FORBIDDEN");
        var finance = new SessionPrincipal("finance", "finance", "财务", null, "FINANCE", null);
        code(() -> promotion.source(form, first.id(), finance), "PROCESS_PLAN_FORM_FORBIDDEN");
        code(() -> promotion.displacedDraft(form, first.id(), finance), "PROCESS_PLAN_FORM_FORBIDDEN");
        jdbc.update("update test_assignment set archived_at=current_timestamp where experiment_form_id=?", form);
        code(() -> promotion.source(form, first.id(), tester), "PROCESS_REVISION_NOT_ASSIGNED");
    }

    @Test void activeVersionChangesAndLockedFormsInvalidatePreviewAndConfirm() {
        var trial = confirm(create());
        var preview = preview(trial);
        plans.save(form, new ProcessPlan(null, form, preview.expectedProcessVersionNo(), "DRAFT", List.of(), null, false));
        code(() -> promotion.submit(form, trial.id(), command(trial, preview, true, "old"), owner), "PROCESS_PLAN_VERSION_CONFLICT");
        code(() -> trials.confirm(form, trial.id(), point(trial).id(), new TrialSchemeService.ConfirmCommand(1), owner), "TRIAL_VERSION_CONFLICT");
        for (var status : List.of("SUBMITTED_FOR_TEST", "LOCKED")) {
            jdbc.update("update experiment_form set status=? where id=?", status, form);
            code(() -> promotion.preview(form, trial.id(), new TrialPromotionService.PreviewCommand(trial.versionNo(), plans.find(form).versionNo()), owner), "TRIAL_FORM_LOCKED");
            code(() -> confirm(trial), "TRIAL_FORM_LOCKED");
        }
    }

    @Test void explicitConfirmationAuditsImmutableSessionIdentity() {
        var trial = create();
        code(() -> trials.confirm(form, trial.id(), point(trial).id(), new TrialSchemeService.ConfirmCommand(9), owner), "TRIAL_VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id=?", Integer.class, trial.id())).isZero();
        var confirmed = confirm(trial);
        assertThat(jdbc.queryForObject("select operator_user_id from audit_log where business_id=? and action='TRIAL_CONTROL_PASS_CONFIRMED'", String.class, trial.id())).isEqualTo(owner.userId());
        assertThat(jdbc.queryForObject("select detail from audit_log where business_id=?", String.class, trial.id())).contains(point(confirmed).id(), "trialVersionNo=2");
    }

    @Test void concurrentRetriesProduceOneRevisionAndOnePromotion() throws Exception {
        var trial = confirm(create());
        var preview = preview(trial);
        var command = command(trial, preview, true, "concurrent");
        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<String> operation = () -> {
            ready.countDown();
            try { go.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            return promotion.submit(form, trial.id(), command, owner).id();
        };
        var first = java.util.concurrent.CompletableFuture.supplyAsync(operation);
        var second = java.util.concurrent.CompletableFuture.supplyAsync(operation);
        ready.await(); go.countDown();
        assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        assertThat(revisions.list(form)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from experiment_trial_promotion where experiment_form_id=?", Integer.class, form)).isEqualTo(1);
    }

    @Test void preservesNonemptyLegacyDraftInsteadOfTreatingItAsAnEmptyPlaceholder() {
        jdbc.update("insert into experiment_process(id,experiment_form_id,sequence,process_name,before_weight_kg,after_weight_kg,remark) values (?,?,1,'历史熟制',10,9,'历史数据')", "L-" + form.substring(2), form);
        var before = plans.find(form);
        assertThat(before.legacy()).isTrue();
        var trial = confirm(create());
        var preview = preview(trial);
        assertThat(preview.differingDraft()).isTrue();
        var revision = promotion.submit(form, trial.id(), command(trial, preview, true, "legacy"), owner);
        assertThat(promotion.displacedDraft(form, revision.id(), owner)).usingRecursiveComparison().isEqualTo(before);
    }

    private TrialScheme create() { return trials.create(form, new TrialSchemeService.CreateCommand("方案A", "试验目的", "温度", graph(), TrialScheme.PlannedData.empty()), owner); }
    private TrialScheme confirm(TrialScheme t) { return trials.confirm(form, t.id(), point(t).id(), new TrialSchemeService.ConfirmCommand(t.versionNo()), owner); }
    private TrialPromotionService.Preview preview(TrialScheme t) { return promotion.preview(form, t.id(), new TrialPromotionService.PreviewCommand(t.versionNo(), plans.find(form).versionNo()), owner); }
    private TrialPromotionService.SubmitCommand command(TrialScheme t, TrialPromotionService.Preview p, boolean confirmed, String key) { return new TrialPromotionService.SubmitCommand(t.versionNo(), p.expectedProcessVersionNo(), p.previewToken(), confirmed, null, key); }
    private TrialScheme save(TrialScheme t, ProcessPlan plan) { return trials.save(form, t.id(), new TrialSchemeService.SaveCommand(t.versionNo(), t.name(), t.purpose(), t.variables(), t.conclusion(), t.recommendationReason(), t.qualityScore(), t.qualityNotes(), t.difficulty(), plan, t.plannedData()), owner); }
    private ProcessPlan.ControlPoint point(TrialScheme t) { return t.plan().majorProcesses().get(0).steps().get(0).controlPoints().get(0); }
    private ObjectNode control(ObjectNode n) { return (ObjectNode)n.path("majorProcesses").get(0).path("steps").get(0).path("controlPoints").get(0); }
    private ProcessPlan edit(ProcessPlan p, Consumer<ObjectNode> change) { var n = (ObjectNode)mapper.valueToTree(p); change.accept(n); return mapper.convertValue(n, ProcessPlan.class); }
    private void code(Runnable action, String code) { assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).code()).isEqualTo(code); }
    private ProcessPlan graph() {
        var cp = new ProcessPlan.ControlPoint("cp", 1, "QUALITY", "CRITICAL", "中心温度", new BigDecimal("75"), new BigDecimal("70"), new BigDecimal("85"), "℃", "探针", "温度计", "每锅", null, true, "伪造", "2026-09-15T10:00", null, List.of(new ProcessPlan.ControlMeasurement("cm", 1, new BigDecimal("76"), "2026-09-15T10:00", "PASS", null, "PASS", null)));
        var step = new ProcessPlan.MinorStep("step", 1, "COOK", "熟制", "NORMAL", null, null, null, null, null, null, null, null, List.of(new ProcessPlan.StepMaterial("mat", 1, "PRIMARY", "BEEF", "牛腩", "SOLID", BigDecimal.TEN, null, null, "EXTERNAL", null)), List.of(new ProcessPlan.StepOutput("out", 1, "FINISHED", "成品", "SEMI_SOLID", BigDecimal.TEN, true, false, null)), List.of(cp));
        return new ProcessPlan("plan", form, 0, "DRAFT", List.of(new ProcessPlan.MajorProcess("major", 1, "COOK", "熟制", null, "PRIMARY_INPUT", null, List.of(step), List.of(), List.of(), null)), null, false);
    }
}
