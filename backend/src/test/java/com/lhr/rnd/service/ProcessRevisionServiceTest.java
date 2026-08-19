package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProcessRevisionServiceTest {
    private static final String FORM_ID = "FORM-PROCESS-REVISION";

    @Autowired ProcessPlanService planService;
    @Autowired ProcessRevisionService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedForm() {
        jdbc.update("delete from experiment_process_revision where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", FORM_ID);
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, FORM_ID) > 0) return;
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)", "REQ-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "预制菜", "客户", "1kg", "研发", "APPROVED", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)", "PRJ-PROCESS-REVISION", "REQ-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)", "VER-PROCESS-REVISION", "PRJ-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)", "TASK-PROCESS-REVISION", "PRJ-PROCESS-REVISION", "VER-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "V1", "IN_PROGRESS", now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)", FORM_ID, "TASK-PROCESS-REVISION", "PRJ-PROCESS-REVISION", "VER-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "V1", "DRAFT", "研发", now);
    }

    @Test
    void submitsAnImmutableNormalizedSnapshotAndRejectsStaleOrUnconfirmedSubmission() {
        var saved = saveReadyDraft();

        var revision = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saved.versionNo(), true, "首次正式提交", "可信研发"));

        assertThat(revision.revisionNo()).isEqualTo(1);
        assertThat(revision.snapshot()).usingRecursiveComparison().isEqualTo(planService.find(FORM_ID));
        assertThat(revision.snapshotHash()).hasSize(64);
        assertThatThrownBy(() -> service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saved.versionNo(), false, null, "可信研发")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_SUBMISSION_CONFIRMATION_REQUIRED");
    }

    @Test
    void keepsRevisionMetadataAndSnapshotProvenanceInSyncAndRejectsOverrideOfPersistedDraftReason() {
        var firstDraft = saveReadyDraft();
        var first = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                firstDraft.versionNo(), true, "首次正式提交", "可信研发"));
        var restored = service.createDraftFromRevision(FORM_ID, first.id(), "已验证的变更原因");

        assertThatThrownBy(() -> service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                restored.versionNo(), true, "客户端覆盖原因", "可信研发")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_CHANGE_REASON_CONFLICT");

        var second = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                restored.versionNo(), true, null, "可信研发"));
        assertThat(second.changeReason()).isEqualTo("已验证的变更原因");
        assertThat(second.snapshot().sourceRevisionId()).isEqualTo(second.sourceRevisionId());
        assertThat(second.snapshot().changeReason()).isEqualTo(second.changeReason());
    }

    @Test
    void restoresOnlyItsOwnRevisionAsADraftAndCarriesPersistedChangeReasonIntoTheNextRevision() {
        var firstDraft = saveReadyDraft();
        var first = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                firstDraft.versionNo(), true, "首次正式提交", "可信研发"));

        assertThatThrownBy(() -> service.createDraftFromRevision(FORM_ID, first.id(), " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_CHANGE_REASON_REQUIRED");

        var restored = service.createDraftFromRevision(FORM_ID, first.id(), "调整熟制时间");
        assertThat(restored.status()).isEqualTo("DRAFT");
        assertThat(restored.versionNo()).isGreaterThan(firstDraft.versionNo());
        assertThat(restored.sourceRevisionId()).isEqualTo(first.id());
        assertThat(restored.changeReason()).isEqualTo("调整熟制时间");

        var second = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                restored.versionNo(), true, null, "可信研发"));
        assertThat(second.revisionNo()).isEqualTo(2);
        assertThat(second.sourceRevisionId()).isEqualTo(first.id());
        assertThat(second.changeReason()).isEqualTo("调整熟制时间");
    }

    @Test
    void restoresAnyHistoricalRevisionAgainstTheCurrentSubmittedLiveVersion() {
        var firstDraft = saveReadyDraft();
        var first = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                firstDraft.versionNo(), true, "首次正式提交", "可信研发"));
        var reopened = service.createDraftFromRevision(FORM_ID, first.id(), "用于第二版");
        var second = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                reopened.versionNo(), true, null, "可信研发"));

        var reopenedFromFirst = service.createDraftFromRevision(FORM_ID, first.id(), "回到第一版内容");

        assertThat(second.revisionNo()).isEqualTo(2);
        assertThat(reopenedFromFirst.status()).isEqualTo("DRAFT");
        assertThat(reopenedFromFirst.versionNo()).isGreaterThan(second.snapshot().versionNo());
        assertThat(reopenedFromFirst.sourceRevisionId()).isEqualTo(first.id());
        assertThat(reopenedFromFirst.majorProcesses()).extracting(ProcessPlan.MajorProcess::processName)
                .containsExactlyElementsOf(first.snapshot().majorProcesses().stream().map(ProcessPlan.MajorProcess::processName).toList());
        assertThat(reopenedFromFirst.majorProcesses().get(0).steps().get(0).outputs().get(0).outputName())
                .isEqualTo(first.snapshot().majorProcesses().get(0).steps().get(0).outputs().get(0).outputName());
    }

    @Test
    void rejectsTamperedSnapshotBeforeReturningOrRestoringIt() {
        var revision = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saveReadyDraft().versionNo(), true, "首次正式提交", "可信研发"));
        jdbc.update("update experiment_process_revision set snapshot_json = replace(snapshot_json, ?, ?) where id = ?",
                "熟制牛腩", "篡改牛腩", revision.id());

        assertThatThrownBy(() -> service.find(FORM_ID, revision.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR");
        jdbc.update("delete from audit_log where business_id = ?", FORM_ID);
        assertThatThrownBy(() -> service.createDraftFromRevision(FORM_ID, revision.id(), "不得恢复损坏版本"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ?", Integer.class, FORM_ID)).isZero();
    }

    @Test
    void writesFormalSubmitAndDraftCreationAuditRowsButDoesNotAuditRejectedSubmissions() {
        jdbc.update("delete from audit_log where business_id = ?", FORM_ID);
        var saved = saveReadyDraft();
        var revision = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saved.versionNo(), true, "首次正式提交", "可信研发"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ? and action = ?", Integer.class,
                FORM_ID, "PROCESS_PLAN_SUBMITTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ? and detail like ?", Integer.class,
                FORM_ID, "%" + revision.id() + "%")).isEqualTo(1);

        service.createDraftFromRevision(FORM_ID, revision.id(), "修改工艺");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ? and action = ?", Integer.class,
                FORM_ID, "PROCESS_PLAN_DRAFT_CREATED")).isEqualTo(1);

        assertThatThrownBy(() -> service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saved.versionNo(), false, "拒绝提交", "可信研发"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ?", Integer.class, FORM_ID)).isEqualTo(2);
    }

    @Test
    void rejectsRevisionLookupForAnotherForm() {
        var first = service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saveReadyDraft().versionNo(), true, "首次正式提交", "可信研发"));
        var otherFormId = "FORM-PROCESS-REVISION-OTHER";
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, otherFormId) == 0) {
            jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",
                    otherFormId, "TASK-PROCESS-REVISION", "PRJ-PROCESS-REVISION", "VER-PROCESS-REVISION", "S-PROCESS-REVISION", "牛腩", "V1", "DRAFT", "研发", LocalDateTime.now());
        }

        assertThatThrownBy(() -> service.find(otherFormId, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_REVISION_NOT_FOUND");
    }

    @Test
    void blocksUnreadyDraftsAndAllowsOnlyOneConcurrentSubmissionForTheSameVersion() throws Exception {
        var empty = planService.find(FORM_ID);
        var savedEmpty = planService.save(FORM_ID, new ProcessPlan(null, FORM_ID, empty.versionNo(), "DRAFT", List.of(), null, false));
        assertThatThrownBy(() -> service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                savedEmpty.versionNo(), true, null, "可信研发")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_PLAN_NOT_READY");

        var saved = saveReadyDraft();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> submitTogether(saved.versionNo(), ready, go));
        var second = CompletableFuture.supplyAsync(() -> submitTogether(saved.versionNo(), ready, go));
        ready.await();
        go.countDown();

        var results = java.util.Arrays.asList(first.join(), second.join());
        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from experiment_process_revision where experiment_form_id = ?", Integer.class, FORM_ID)).isEqualTo(1);
    }

    @Test
    void concurrentSaveAndSubmitNeverLeavesAChangedSubmittedLivePlan() throws Exception {
        var saved = saveReadyDraft();
        var request = new ProcessPlan(saved.id(), FORM_ID, saved.versionNo(), "DRAFT", saved.majorProcesses(), null,
                saved.balanceToleranceKg(), false, saved.sourceRevisionId(), saved.changeReason());
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var save = CompletableFuture.supplyAsync(() -> saveOrConflict(request, ready, go));
        var submit = CompletableFuture.supplyAsync(() -> submitTogether(saved.versionNo(), ready, go));

        ready.await();
        go.countDown();
        save.join();
        submit.join();

        var revisionCount = jdbc.queryForObject("select count(*) from experiment_process_revision where experiment_form_id = ?", Integer.class, FORM_ID);
        var live = planService.find(FORM_ID);
        assertThat(revisionCount).isLessThanOrEqualTo(1);
        if (revisionCount == 1) {
            assertThat(live.status()).isEqualTo("SUBMITTED");
            assertThat(live.versionNo()).isEqualTo(saved.versionNo());
        } else {
            assertThat(live.status()).isEqualTo("DRAFT");
            assertThat(live.versionNo()).isEqualTo(saved.versionNo() + 1);
        }
    }

    private ProcessRevisionResult submitTogether(int versionNo, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await();
            service.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(versionNo, true, "首次正式提交", "并发研发"));
            return null;
        } catch (Exception exception) {
            if (exception instanceof BusinessException businessException
                    && "PROCESS_PLAN_VERSION_CONFLICT".equals(businessException.code())) return new ProcessRevisionResult();
            throw new RuntimeException(exception);
        }
    }

    private ProcessRevisionResult saveOrConflict(ProcessPlan request, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await();
            planService.save(FORM_ID, request);
            return null;
        } catch (Exception exception) {
            if (exception instanceof BusinessException businessException
                    && "PROCESS_PLAN_VERSION_CONFLICT".equals(businessException.code())) return new ProcessRevisionResult();
            throw new RuntimeException(exception);
        }
    }

    private record ProcessRevisionResult() {
    }

    private ProcessPlan saveReadyDraft() {
        var step = new ProcessPlan.MinorStep(null, 1, "COOK", "熟制", "NORMAL", null, null, null, null, null, null,
                null, null, List.of(new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "鲜牛腩", "SOLID",
                new BigDecimal("10.0000"), "MAT-BEEF", null, "EXTERNAL", null)), List.of(
                new ProcessPlan.StepOutput("OUT-REVISION", 1, "FINISHED", "熟制牛腩", "SEMI_SOLID", new BigDecimal("10.0000"), true, false, null)), List.of(
                new ProcessPlan.ControlPoint("CP-REVISION", 1, "QUALITY", "NORMAL", "中心温度", new BigDecimal("75"),
                        new BigDecimal("70"), new BigDecimal("85"), "℃", "探针测温", "数字探针", "每锅", null,
                        true, "可信研发", "2026-08-19T22:00:00", "研发记录", List.of(
                                new ProcessPlan.ControlMeasurement("CM-REVISION", 1, new BigDecimal("76"), "2026-08-19T22:00:00", "PASS", null, null, "正常")))));
        var major = new ProcessPlan.MajorProcess(null, 1, "COOK", "熟制", null, "PRIMARY_INPUT", null,
                List.of(step), List.of(), List.of(), null);
        var current = planService.find(FORM_ID);
        return planService.save(FORM_ID, new ProcessPlan(null, FORM_ID, current.versionNo(), "DRAFT", List.of(major), null,
                new BigDecimal("0.0100"), false));
    }
}
