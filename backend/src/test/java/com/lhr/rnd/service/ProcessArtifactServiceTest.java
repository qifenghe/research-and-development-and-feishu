package com.lhr.rnd.service;

import com.lhr.rnd.domain.ProcessRecipeService;
import com.lhr.rnd.model.ProcessArtifact;
import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.model.ProcessPlan;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class ProcessArtifactServiceTest {
    private static final String FORM_ID = "FORM-PROCESS-ARTIFACT";
    private static final SessionPrincipal ENGINEER = new SessionPrincipal("USER-ARTIFACT", "artifact", "制品研发", "ou-artifact", "RND_ENGINEER", null);

    @Autowired ProcessPlanService planService;
    @Autowired ProcessRevisionService revisionService;
    @Autowired ProcessArtifactService service;
    @Autowired TrialSchemeService trials;
    @SpyBean ProcessArtifactCleanupLedgerService cleanupLedger;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @SpyBean LocalArchiveStorageService storage;

    @BeforeEach
    void seedForm() {
        clearInvocations(storage, cleanupLedger);
        jdbc.update("delete from process_artifact_cleanup_ledger");
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) select ?,?,?,?,?,?,?,? where not exists (select 1 from user_account where id = ?)",
                ENGINEER.userId(), "artifact", "x", ENGINEER.name(), "RND_ENGINEER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), ENGINEER.userId());
        jdbc.update("delete from audit_log where business_id in (?, ?) ", FORM_ID, "FORM-PROCESS-ARTIFACT");
        jdbc.update("delete from experiment_process_artifact where process_revision_id in (select id from experiment_process_revision where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_revision where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", FORM_ID);
        jdbc.update("update rnd_task set assignee_name = ?, assignee_user_id = null where id = 'TASK-PROCESS-ARTIFACT'", ENGINEER.name());
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, FORM_ID) == 0) {
            var now = LocalDateTime.now();
            jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)", "REQ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "客户", "1kg", "研发", "PENDING_ASSIGNMENT", now);
            jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)", "PRJ-PROCESS-ARTIFACT", "REQ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
            jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)", "VER-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
            jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,assignee_name,created_at) values (?,?,?,?,?,?,?,?,?)", "TASK-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "VER-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "V1", "SAMPLING", "制品研发", now);
            jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)", FORM_ID, "TASK-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "VER-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "V1", "DRAFT", "研发", now);
        }
    }

    @Test
    void emptySavedDraftAndTrialPreviewShowPendingExternalBatch() throws Exception {
        var saved = planService.save(FORM_ID, new ProcessPlan("P", FORM_ID, 1, "DRAFT", List.of(), null, false));
        var trial = trials.create(FORM_ID, new TrialSchemeService.CreateCommand("空方案软件测试", null, null, saved, null), ENGINEER);
        for (var view : List.of(service.draftView(FORM_ID, saved.versionNo(), ENGINEER), service.trialView(FORM_ID, trial.id(), trial.versionNo(), ENGINEER))) {
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.preview(view, "FORMULA_XLSX").content()))) {
                var sheet = book.getSheetAt(0);
                assertThat(sheet.getRow(findRow(sheet, "外部原料实际总投入")).getCell(2).getStringCellValue()).isEqualTo("待填写");
                assertThat(sheet.getRow(findRow(sheet, "主流程实际得率")).getCell(2).getStringCellValue()).isEqualTo("待填写");
            }
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.preview(view, "PRICING_XLSX").content()))) {
                assertThat(book.getSheetAt(0).getRow(3).getCell(1).getStringCellValue()).isEqualTo("待填写");
            }
            try (var doc = new XWPFDocument(new ByteArrayInputStream(service.preview(view, "SOP_DOCX").content()))) {
                var text = doc.getParagraphs().stream().map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"))
                        + doc.getTables().stream().map(org.apache.poi.xwpf.usermodel.XWPFTable::getText).collect(java.util.stream.Collectors.joining("\n"));
                assertThat(text).contains("外部原料实际总投入：待填写").doesNotContain("外部原料实际总投入：0 kg");
            }
        }
    }

    @Test
    void incompleteSopPreviewLabelsMissingExternalBatchAndDoesNotInventZero() throws Exception {
        var step = new ProcessPlan.MinorStep("s", 1, "COOK", "熟制", "NORMAL", null, null, null, null, null, null, null, null,
                List.of(new ProcessPlan.StepMaterial("m", 1, "PRIMARY", "BEEF", "牛肉", "SOLID", null, "BEEF", null)),
                List.of(new ProcessPlan.StepOutput("o", 1, "FINISHED", "成品", "SOLID", new BigDecimal("72"), true, false, null)), List.of());
        var p = new ProcessPlan("p", FORM_ID, 1, "DRAFT", List.of(new ProcessPlan.MajorProcess("m", 1, "COOK", "熟制", null, "PRIMARY_INPUT", null, List.of(step), List.of(), List.of(), null)), null, false);
        var view = new ProcessExportCheckService().view("软件测试", "试验A V1", p, null, List.of());
        try (var doc = new XWPFDocument(new ByteArrayInputStream(service.preview(view, "SOP_DOCX").content()))) {
            var text = doc.getParagraphs().stream().map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"))
                    + doc.getTables().stream().map(org.apache.poi.xwpf.usermodel.XWPFTable::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(text).contains("外部原料实际总投入：待填写", "主流程实际得率：待填写").doesNotContain("外部原料实际总投入：0 kg");
        }
    }

    @Test
    void savedTrialAndDraftPreviewsAreVersionBoundAuthorizedAndNeverWriteArtifactsOrApprovals() throws Exception {
        var saved = planService.save(FORM_ID, ProcessExportCheckServiceTest.plan("72", "90", "PRIMARY_INPUT"));
        var trial = trials.create(FORM_ID, new TrialSchemeService.CreateCommand("软件测试A", null, null, saved, null), ENGINEER);
        var counts = List.of("experiment_process_artifact", "process_artifact_cleanup_ledger", "experiment_process_revision", "experiment_trial_submission_preview", "experiment_trial_promotion", "audit_log");
        var before = counts.stream().map(t -> jdbc.queryForObject("select count(*) from " + t, Integer.class)).toList();
        var draftView = service.draftView(FORM_ID, saved.versionNo(), ENGINEER);
        assertThat(draftView.metadata().compiledBy()).isEqualTo(ENGINEER.name());
        assertThat(draftView.metadata().specification()).isEqualTo("1kg");
        var trialView = service.trialView(FORM_ID, trial.id(), trial.versionNo(), ENGINEER);
        assertThat(trialView.finishedQuantity()).isNull();
        for (var type : List.of("FORMULA_XLSX", "SOP_DOCX", "PRICING_XLSX")) {
            assertThat(service.preview(trialView, type).content()).isNotEmpty();
            assertThat(service.preview(draftView, type).fileName()).contains("预览");
        }
        assertThat(counts.stream().map(t -> jdbc.queryForObject("select count(*) from " + t, Integer.class)).toList()).isEqualTo(before);
        assertThatThrownBy(() -> service.trialView(FORM_ID, trial.id(), trial.versionNo() + 1, ENGINEER)).hasMessageContaining("版本");
        assertThatThrownBy(() -> service.draftView(FORM_ID, saved.versionNo() + 1, ENGINEER)).hasMessageContaining("版本");
        assertThatThrownBy(() -> service.draftView(FORM_ID, saved.versionNo(), new SessionPrincipal("F", "f", "财务", null, "FINANCE", null))).isInstanceOf(com.lhr.rnd.api.BusinessException.class);
        assertThat(trials.find(FORM_ID, trial.id(), ENGINEER).versionNo()).isEqualTo(trial.versionNo());
    }

    @Test
    void immutableOwnerIdAllowsOnlyTheRealSameNameAccountToListGenerateAndDownload() {
        var owner = new SessionPrincipal("ARTIFACT-OWNER-SAME", "artifact_owner_same", "同名文件负责人", null, "RND_ENGINEER", null);
        var intruder = new SessionPrincipal("ARTIFACT-INTRUDER-SAME", "artifact_intruder_same", "同名文件负责人", null, "RND_ENGINEER", null);
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,?,current_timestamp,current_timestamp)",
                owner.userId(), owner.username(), "x", owner.name(), owner.role(), "ACTIVE");
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,?,current_timestamp,current_timestamp)",
                intruder.userId(), intruder.username(), "x", intruder.name(), intruder.role(), "ACTIVE");
        var revision = formalRevision();
        jdbc.update("update rnd_task set assignee_name = ?, assignee_user_id = ? where id = 'TASK-PROCESS-ARTIFACT'", owner.name(), owner.userId());

        assertForbidden(() -> service.list(FORM_ID, revision.id(), intruder));
        assertForbidden(() -> service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, intruder));
        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, owner);
        assertForbidden(() -> service.download(FORM_ID, revision.id(), artifact.id(), intruder));

        assertThat(service.list(FORM_ID, revision.id(), owner)).extracting(ProcessArtifact::id).containsExactly(artifact.id());
        assertThat(service.download(FORM_ID, revision.id(), artifact.id(), owner).content()).isNotEmpty();
    }

    @Test
    void allocatesEveryFormulaRowDeterministicallyWithoutNegativeValuesAndTotalsExactlyOneHundred() throws Exception {
        var revision = formalRevisionWithExternalWeights(List.of(
                new BigDecimal("20.1225"), new BigDecimal("31.6289"), new BigDecimal("52.4496"),
                new BigDecimal("2.0622"), new BigDecimal("0.0001")));

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER).content()))) {
            var sheet = workbook.getSheet("每100kg折算");
            var normalizedHeader = findRow(sheet, "每100kg外部投入折算 kg");
            BigDecimal ratioTotal = BigDecimal.ZERO;
            BigDecimal hundredTotal = BigDecimal.ZERO;
            for (int row = normalizedHeader + 1; row < normalizedHeader + 6; row++) {
                assertThat(sheet.getRow(row).getCell(4).getNumericCellValue()).isNotNegative();
                ratioTotal = ratioTotal.add(BigDecimal.valueOf(sheet.getRow(row).getCell(4).getNumericCellValue()));
                hundredTotal = hundredTotal.add(BigDecimal.valueOf(sheet.getRow(row).getCell(4).getNumericCellValue()));
            }
            assertThat(ratioTotal).isEqualByComparingTo("100.0000");
            assertThat(hundredTotal).isEqualByComparingTo("100.0000");
            assertThat(sheet.getRow(normalizedHeader + 6).getCell(4).getNumericCellValue()).isEqualTo(100.0000);
        }

        revisionService.createDraftFromRevision(FORM_ID, revision.id(), "三等分测试草稿", ENGINEER);
        var equalRevision = formalRevisionWithExternalWeights(List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        var equalArtifact = service.generate(FORM_ID, equalRevision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.download(FORM_ID, equalRevision.id(), equalArtifact.id(), ENGINEER).content()))) {
            var sheet = workbook.getSheet("每100kg折算");
            var normalizedHeader = findRow(sheet, "每100kg外部投入折算 kg");
            assertThat(List.of(1, 2, 3).stream().map(offset -> sheet.getRow(normalizedHeader + offset).getCell(4).getNumericCellValue()).toList())
                    .containsExactly(33.3334, 33.3333, 33.3333);
        }
    }

    @Test
    void failedAttemptConsumesVersionKeepsMetadataAndAuditIdentityAndNeverReadsStorageOnDownload() {
        var revision = formalRevision();
        doThrow(new com.lhr.rnd.api.BusinessException("ARCHIVE_FILE_WRITE_FAILED", "injected"))
                .doCallRealMethod().when(storage).store(any(), any());

        var failed = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        assertThat(failed.status()).isEqualTo(ProcessArtifact.FAILED);
        assertThat(failed.documentVersion()).isEqualTo("1");
        assertThat(failed.generatedByUserId()).isEqualTo(ENGINEER.userId());
        assertThatThrownBy(() -> service.download(FORM_ID, revision.id(), failed.id(), ENGINEER))
                .isInstanceOf(com.lhr.rnd.api.BusinessException.class)
                .extracting(error -> ((com.lhr.rnd.api.BusinessException) error).code())
                .isEqualTo("PROCESS_ARTIFACT_NOT_READY");
        verify(storage, never()).read(any());

        var ready = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        assertThat(ready.documentVersion()).isEqualTo("2");
        assertThat(service.list(FORM_ID, revision.id(), ENGINEER)).extracting(ProcessArtifact::status)
                .containsExactly(ProcessArtifact.READY, ProcessArtifact.FAILED);
        assertThat(jdbc.queryForList("select operator_user_id from audit_log where business_id = ? and action in (?, ?) order by created_at", String.class,
                FORM_ID, "PROCESS_ARTIFACT_GENERATION_FAILED", "PROCESS_ARTIFACT_GENERATED")).containsExactly(ENGINEER.userId(), ENGINEER.userId());
    }

    @Test
    void commitFailureRollsBackArtifactAndAuditAndConvergesFileLedger() {
        var revision = formalRevision();
        var generated = new AtomicReference<ProcessArtifact>();
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            generated.set(service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("injected commit failure"); }
            });
        })).hasMessageContaining("injected commit failure");

        assertThat(jdbc.queryForObject("select count(*) from experiment_process_artifact where id = ?", Integer.class, generated.get().id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where detail like ?", Integer.class, "%" + generated.get().id() + "%")).isZero();
        assertThat(Files.exists(Path.of("target", "rnd-archive").resolve(generated.get().storageKey()))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from process_artifact_cleanup_ledger where storage_key = ?", Integer.class, generated.get().storageKey())).isZero();
    }

    @Test
    void startupCannotDeleteARegisteredReservationBeforeStorageAndRollbackStillConverges() throws Exception {
        var revision = formalRevision();
        var registered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var reservation = new AtomicReference<ProcessArtifactCleanupLedgerService.Reservation>();
        doAnswer(invocation -> {
            var result = (ProcessArtifactCleanupLedgerService.Reservation) invocation.getArgument(0);
            reservation.set(result);
            registered.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("pre-storage lease pause timed out");
            invocation.callRealMethod();
            return null;
        }).when(cleanupLedger).renew(any(ProcessArtifactCleanupLedgerService.Reservation.class));

        var generated = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
            status.setRollbackOnly();
            return artifact;
        }));
        assertThat(registered.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            cleanupLedger.reconcileOnStartup();
            assertThat(ledgerState(reservation.get().storageKey())).isEqualTo("RESERVED");
            assertThat(Files.exists(archivePath(reservation.get().storageKey()))).isFalse();
            verify(storage, never()).delete(eq(reservation.get().storageKey()));
        } finally {
            resume.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isFalse();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
    }

    @Test
    void startupCannotDeleteStoredReservationBeforeCommitAndRollbackStillConverges() throws Exception {
        var revision = formalRevision();
        var stored = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var storageKey = new AtomicReference<String>();
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            storageKey.set(invocation.getArgument(0));
            stored.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("store pause timed out");
            return result;
        }).when(storage).store(anyString(), any());

        var generated = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
            status.setRollbackOnly();
            return artifact;
        }));
        assertThat(stored.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            cleanupLedger.reconcileOnStartup();
            assertThat(ledgerState(storageKey.get())).isEqualTo("RESERVED");
            assertThat(Files.exists(archivePath(storageKey.get()))).isTrue();
            verify(storage, never()).delete(eq(storageKey.get()));
        } finally {
            resume.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isFalse();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
    }

    @Test
    void startupCannotDeleteStoredReservationAndAFollowingCommitPreservesReadyBytes() throws Exception {
        var revision = formalRevision();
        var stored = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var storageKey = new AtomicReference<String>();
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            storageKey.set(invocation.getArgument(0));
            stored.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("store pause timed out");
            return result;
        }).when(storage).store(anyString(), any());

        var generated = CompletableFuture.supplyAsync(() -> service.generate(
                FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER));
        assertThat(stored.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            cleanupLedger.reconcileOnStartup();
            assertThat(ledgerState(storageKey.get())).isEqualTo("RESERVED");
            assertThat(Files.exists(archivePath(storageKey.get()))).isTrue();
            verify(storage, never()).delete(eq(storageKey.get()));
        } finally {
            resume.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        assertThat(artifact.status()).isEqualTo(ProcessArtifact.READY);
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isTrue();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
    }

    @Test
    void ownershipLostWhileStoreIsInFlightFailsWithoutReadyMetadataOrOrphanedBytes() throws Exception {
        var revision = formalRevision();
        var storeEntered = new CountDownLatch(1);
        var resumeStore = new CountDownLatch(1);
        var storageKey = new AtomicReference<String>();
        doAnswer(invocation -> {
            storageKey.set(invocation.getArgument(0));
            storeEntered.countDown();
            if (!resumeStore.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("store ownership pause timed out");
            return invocation.callRealMethod();
        }).when(storage).store(anyString(), any());

        var generated = CompletableFuture.supplyAsync(() -> service.generate(
                FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER));
        assertThat(storeEntered.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> futureReconciler().reconcileOnStartup());
            assertThat(ledgerCount(storageKey.get())).isZero();
        } finally {
            resumeStore.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        assertThat(artifact.status()).isEqualTo(ProcessArtifact.FAILED);
        assertThat(jdbc.queryForObject(
                "select count(*) from experiment_process_artifact where storage_key = ? and status = 'READY'",
                Integer.class, storageKey.get())).isZero();
        assertThat(Files.exists(archivePath(storageKey.get()))).isFalse();
        assertThat(ledgerCount(storageKey.get())).isZero();
    }

    @Test
    void expiredReservationReconcileWaitsForReadyCommitThenKeepsDownloadableBytes() throws Exception {
        var revision = formalRevision();
        var locked = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var generated = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
            locked.countDown();
            try {
                if (!releaseCommit.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("ready commit pause timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return artifact;
        }));
        assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();
        var reconcileStarted = new CountDownLatch(1);
        var reconcile = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> futureReconciler(reconcileStarted).reconcileOnStartup()));
        assertThat(reconcileStarted.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> reconcile.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseCommit.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        reconcile.get(20, TimeUnit.SECONDS);
        assertThat(artifact.status()).isEqualTo(ProcessArtifact.READY);
        assertThat(service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER).content()).isNotEmpty();
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isTrue();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
    }

    @Test
    void expiredReservationReconcileWaitsForOuterRollbackThenDeletesStoredBytes() throws Exception {
        var revision = formalRevision();
        var locked = new CountDownLatch(1);
        var releaseRollback = new CountDownLatch(1);
        var generated = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
            locked.countDown();
            try {
                if (!releaseRollback.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("ready rollback pause timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            status.setRollbackOnly();
            return artifact;
        }));
        assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();
        var reconcileStarted = new CountDownLatch(1);
        var reconcile = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> futureReconciler(reconcileStarted).reconcileOnStartup()));
        assertThat(reconcileStarted.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> reconcile.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseRollback.countDown();
        }

        var artifact = generated.get(20, TimeUnit.SECONDS);
        reconcile.get(20, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject(
                "select count(*) from experiment_process_artifact where id = ?", Integer.class, artifact.id())).isZero();
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isFalse();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
    }

    @Test
    void staleOwnerTokenCannotLockOrDelayAReplacementReservation() throws Exception {
        var key = "process-artifacts/replaced-" + UUID.randomUUID() + ".xlsx";
        var stale = cleanupLedger.register(key);
        cleanupLedger.confirm(stale);
        var replacement = cleanupLedger.register(key);
        storage.store(key, new byte[]{4});
        var staleChecked = new CountDownLatch(1);
        var releaseStaleTransaction = new CountDownLatch(1);
        var staleAttempt = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(futureReconciler().lockForReady(stale)).isFalse();
            staleChecked.countDown();
            try {
                if (!releaseStaleTransaction.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("stale transaction pause timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }));
        assertThat(staleChecked.await(20, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> replacementCleanup = null;
        try {
            replacementCleanup = CompletableFuture.runAsync(() -> cleanupLedger.orphanAndDelete(replacement));
            replacementCleanup.get(2, TimeUnit.SECONDS);
        } finally {
            releaseStaleTransaction.countDown();
            staleAttempt.get(20, TimeUnit.SECONDS);
            if (replacementCleanup != null) replacementCleanup.get(20, TimeUnit.SECONDS);
        }
        assertThat(Files.exists(archivePath(key))).isFalse();
        assertThat(ledgerCount(key)).isZero();
    }

    @Test
    void failedCommitConfirmationLeavesARecoverableReservationAndNeverDeletesReadyBytes() {
        var revision = formalRevision();
        doThrow(new IllegalStateException("injected confirm failure"))
                .doCallRealMethod().when(cleanupLedger)
                .confirm(any(ProcessArtifactCleanupLedgerService.Reservation.class));

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);

        assertThat(artifact.status()).isEqualTo(ProcessArtifact.READY);
        assertThat(ledgerState(artifact.storageKey())).isEqualTo("RESERVED");
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isTrue();
        expire(artifact.storageKey());
        cleanupLedger.reconcileStale();
        assertThat(ledgerCount(artifact.storageKey())).isZero();
        assertThat(Files.exists(archivePath(artifact.storageKey()))).isTrue();
    }

    @Test
    void durableLedgerCleansOnlyExpiredOrOrphanedReservationsAndRetriesDeleteFailure() throws Exception {
        var crashKey = "process-artifacts/crash-" + UUID.randomUUID() + ".xlsx";
        var crashReservation = cleanupLedger.register(crashKey);
        assertThat(crashReservation.ownerToken()).isNotBlank();
        assertThat(crashReservation.leaseUntil()).isAfter(LocalDateTime.now());
        storage.store(crashKey, new byte[]{1});
        expire(crashKey);

        cleanupLedger.reconcileOnStartup();
        assertThat(Files.exists(archivePath(crashKey))).isFalse();
        assertThat(ledgerCount(crashKey)).isZero();

        var freshKey = "process-artifacts/fresh-" + UUID.randomUUID() + ".xlsx";
        var freshReservation = cleanupLedger.register(freshKey);
        assertThat(freshReservation.ownerToken()).isNotEqualTo(crashReservation.ownerToken());
        storage.store(freshKey, new byte[]{2});
        var staleOwner = new ProcessArtifactCleanupLedgerService.Reservation(
                freshKey, "stale-owner-token", freshReservation.leaseUntil());
        cleanupLedger.confirm(staleOwner);
        cleanupLedger.orphanAndDelete(staleOwner);
        cleanupLedger.reconcileOnStartup();
        assertThat(ledgerState(freshKey)).isEqualTo("RESERVED");
        assertThat(Files.exists(archivePath(freshKey))).isTrue();
        cleanupLedger.reconcileStale();
        assertThat(Files.exists(archivePath(freshKey))).isTrue();
        cleanupLedger.orphanAndDelete(freshReservation);
        assertThat(Files.exists(archivePath(freshKey))).isFalse();
        assertThat(ledgerCount(freshKey)).isZero();

        var revision = formalRevision();
        var ready = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        cleanupLedger.register(ready.storageKey());
        expire(ready.storageKey());
        cleanupLedger.reconcileOnStartup();
        assertThat(Files.exists(archivePath(ready.storageKey()))).isTrue();
        assertThat(ledgerCount(ready.storageKey())).isZero();

        var retryKey = "process-artifacts/retry-" + UUID.randomUUID() + ".xlsx";
        var retryReservation = cleanupLedger.register(retryKey);
        storage.store(retryKey, new byte[]{3});
        doThrow(new com.lhr.rnd.api.BusinessException("ARCHIVE_FILE_DELETE_FAILED", "injected"))
                .doCallRealMethod().when(storage).delete(eq(retryKey));
        cleanupLedger.orphanAndDelete(retryReservation);
        assertThat(ledgerState(retryKey)).isEqualTo("ORPHANED");
        assertThat(jdbc.queryForObject("select error from process_artifact_cleanup_ledger where storage_key = ?", String.class, retryKey)).contains("injected");
        assertThat(Files.exists(archivePath(retryKey))).isTrue();
        cleanupLedger.reconcileStale();
        assertThat(Files.exists(archivePath(retryKey))).isFalse();
        assertThat(ledgerCount(retryKey)).isZero();
    }

    @Test
    void generatesAFormulaWorkbookFromOnlyAggregatedExternalMaterials() throws Exception {
        var revision = formalRevision();

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        var download = service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER);

        assertThat(artifact.status()).isEqualTo("READY");
        assertThat(download.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        var output = Path.of("target", "task-5-export-qa", "formal-rnd-formula-actual-and-100kg.xlsx");
        Files.createDirectories(output.getParent());
        Files.write(output, download.content());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(download.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            var actual = workbook.getSheet("本次实际投料");
            var normalized = workbook.getSheet("每100kg折算");
            assertThat(actual.getRow(0).getCell(0).getStringCellValue()).isEqualTo("研发配方—本次实验实际外部投入");
            assertThat(normalized.getRow(0).getCell(0).getStringCellValue()).isEqualTo("研发配方—每100kg外部投入折算");
            var actualHeader = findRow(actual, "本次实验实际 kg");
            assertThat(actual.getRow(actualHeader).getCell(1).getStringCellValue()).isEqualTo("物料编码");
            assertThat(actual.getRow(actualHeader + 1).getCell(1).getStringCellValue()).isEqualTo("BEEF");
            assertThat(actual.getRow(actualHeader + 1).getCell(3).getStringCellValue()).isEqualTo("主料");
            assertThat(actual.getRow(actualHeader + 1).getCell(4).getNumericCellValue()).isEqualTo(10d);
            assertThat(actual.getRow(actualHeader + 1).getCell(5).getStringCellValue()).contains("腌制");
            var normalizedHeader = findRow(normalized, "每100kg外部投入折算 kg");
            assertThat(normalized.getRow(normalizedHeader + 1).getCell(4).getNumericCellValue()).isEqualTo(100d);
            assertThat(normalized.getRow(normalizedHeader + 2).getCell(4).getNumericCellValue()).isEqualTo(100d);
            for (var sheet : List.of(actual, normalized)) {
                assertThat(sheet.getFooter().getCenter()).contains("&P", "&N");
                assertThat(sheet.getRepeatingRows().getFirstRow()).isEqualTo(findRow(sheet,
                        sheet == actual ? "本次实验实际 kg" : "每100kg外部投入折算 kg"));
                var externalTotalRow = findRow(sheet, "外部原料实际总投入");
                assertThat(sheet.getMergedRegions()).anySatisfy(region -> assertThat(region.formatAsString()).isEqualTo("A" + (externalTotalRow + 1) + ":B" + (externalTotalRow + 1)));
                assertThat(sheet.getMergedRegions()).anySatisfy(region -> assertThat(region.formatAsString()).isEqualTo("C" + (externalTotalRow + 1) + ":F" + (externalTotalRow + 1)));
                assertThat(sheet.getSheetName()).doesNotContain("标准", "中间");
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).doesNotContain("标准");
            }
        }
    }

    @Test
    void generatesSopWithProductionControlsAndASeparateMeasurementTraceAppendix() throws Exception {
        var fixture = derivedFormalRevisionForSop();
        var revision = fixture.revision();

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.SOP_DOCX, ENGINEER);
        var download = service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER);

        assertThat(download.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        try (var document = new XWPFDocument(new ByteArrayInputStream(download.content()))) {
            var text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).collect(java.util.stream.Collectors.joining("\n"));
            var tablesText = document.getTables().stream().map(org.apache.poi.xwpf.usermodel.XWPFTable::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(text + tablesText).contains(
                    "研发 SOP", "正式工艺修订：R2", "文件版本：V1", "变更原因：SOP-REV2-变更原因唯一值",
                    "编制人：" + ENGINEER.name(), "外部原料实际总投入：12.5 kg", "主流程实际得率：64%",
                    "大工序 1 热加工大工序-SOP", "1.1 腌制步骤-SOP", "1.2 熟制步骤-SOP",
                    "外部鲜牛腩-SOP", "BEEF-SOP-001", "主料", "12.5 kg", "均匀腌制操作要求-SOP-唯一",
                    "中心温度参数-SOP 88.8℃-SOP", "夹层锅设备-SOP-唯一", "最终熟制成品-SOP", "8 kg",
                    "测量记录追溯附录", "附录为本次实验实测与审计记录，不作为生产指令标准。");
            assertThat(text + tablesText).doesNotContain(fixture.sourceRevisionId(), fixture.outputId(), "PRIMARY", "FOOD_SAFETY", "CRITICAL", "PASS", "生产 SOP", "100kg 标准配方");
            var productionStandards = document.getTables().stream()
                    .filter(table -> table.getRow(0).getTableCells().stream().map(cell -> cell.getText()).collect(java.util.stream.Collectors.joining()).contains("控制项目"))
                    .findFirst().orElseThrow();
            var standardRow = productionStandards.getRow(1);
            assertThat(standardRow.getCell(0).getText()).isEqualTo("中心温度控制项目-SOP");
            assertThat(standardRow.getCell(1).getText()).contains("类型：食品安全", "重要性：关键",
                    "目标：77.7℃-CONTROL-SOP", "允许范围：70.1 至 88.8℃-CONTROL-SOP",
                    "探针检测方法-SOP", "数字探针工具-SOP", "每锅检测频次-SOP", "继续加热偏差处理-SOP", "研发依据-SOP-唯一");
            assertThat(text + tablesText).contains("前段修割损耗-SOP", "废弃 / 固态");
            assertThat(productionStandards.getText()).doesNotContain(
                    "追溯确认人-SOP", "2026-08-19T22:05:11", "79.9000", "2026-08-19T22:00:22", "PASS",
                    "测量偏差处理-SOP", "复测结果-SOP", "实测备注-SOP");
            var appendix = document.getTables().stream().filter(table -> table.getRow(0).getTableCells().stream().map(cell -> cell.getText()).collect(java.util.stream.Collectors.joining()).contains("实测与追溯信息")).findFirst().orElseThrow().getText();
            assertThat(appendix).contains(
                    "追溯确认人-SOP", "2026-08-19 22:05:11", "79.9", "2026-08-19 22:00:22", "符合",
                    "测量偏差处理-SOP", "复测结果-SOP", "实测备注-SOP");
        }
    }

    @Test
    void rendersTwoMajorFormulaFromOnlyThreeExternalInputsAndWritesQaSample() throws Exception {
        var firstOutputId = "OUT-QA-PREP";
        var secondOutputId = "OUT-QA-COOK";
        var preparation = new ProcessPlan.MinorStep(
                "STEP-QA-1", 1, "PREP", "预处理与复合调味", "NORMAL", null, null, null,
                null, null, null, "真空滚揉机", "按本次试验记录完成复合调味",
                List.of(
                        new ProcessPlan.StepMaterial("MAT-QA-BEEF", 1, "PRIMARY", "BEEF-001", "特级超长名称冷冻牛腩主料", "FROZEN_SOLID", new BigDecimal("100"), "F-BEEF", null, "EXTERNAL", null),
                        new ProcessPlan.StepMaterial("MAT-QA-SPICE", 2, "AUXILIARY", "SPICE-002", "复合香辛料超长添加物名称", "POWDER", new BigDecimal("1"), "F-SPICE", null, "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput(firstOutputId, 1, "INTERMEDIATE", "调味牛腩", "MARINATED", new BigDecimal("100"), true, true, null)), List.of());
        var cooking = new ProcessPlan.MinorStep(
                "STEP-QA-2", 2, "COOK", "慢火熟制", "NORMAL", null, null, null,
                null, null, null, "夹层锅", "按本次试验记录完成熟制",
                List.of(new ProcessPlan.StepMaterial("MAT-QA-PREP", 1, "PRIMARY", null, "调味牛腩", "MARINATED", new BigDecimal("100"), null, null, "STEP_OUTPUT", firstOutputId)),
                List.of(new ProcessPlan.StepOutput(secondOutputId, 1, "INTERMEDIATE", "熟制牛腩", "COOKED", new BigDecimal("80"), true, true, null)), List.of());
        var packaging = new ProcessPlan.MinorStep(
                "STEP-QA-3", 1, "PACK", "冷却与包装", "NORMAL", null, null, null,
                null, null, null, "冷却台与封口机", "按本次试验记录完成冷却包装",
                List.of(
                        new ProcessPlan.StepMaterial("MAT-QA-COOK", 1, "PRIMARY", null, "熟制牛腩", "COOKED", new BigDecimal("80"), null, null, "STEP_OUTPUT", secondOutputId),
                        new ProcessPlan.StepMaterial("MAT-QA-WATER", 2, "AUXILIARY", "WATER-003", "工艺用水超长名称", "LIQUID", new BigDecimal("1"), "F-WATER", null, "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput("OUT-QA-PACKED", 1, "FINISHED", "包装熟制牛腩", "PACKED", new BigDecimal("72"), true, false, null)), List.of());
        var plan = new ProcessPlan("PLAN-QA-TWO-MAJOR", FORM_ID, 1, "DRAFT", List.of(
                new ProcessPlan.MajorProcess("MAJOR-QA-1", 1, "HEAT", "复合调味牛腩热加工序", null, "PRIMARY_INPUT", null, List.of(preparation, cooking), List.of(), List.of(), null),
                new ProcessPlan.MajorProcess("MAJOR-QA-2", 2, "PACK", "冷却包装工序", null, "PRIMARY_INPUT", null, List.of(packaging), List.of(), List.of(), null)),
                new BigDecimal("72"), false);
        var view = new ProcessExportCheckService().view(
                "超长产品名称复合调味牛腩样品", "试验方案 长名方案 V7", plan, null, List.of())
                .withMetadata(new ProcessExportView.Metadata("1 kg 袋装", "研发张三", "2026-08-20"));

        var bytes = service.preview(view, ProcessArtifact.FORMULA_XLSX).content();
        var output = Path.of("target", "task-5-export-qa", "two-major-102kg-rnd-formula-v2.xlsx");
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var actual = workbook.getSheet("本次实际投料");
            var normalized = workbook.getSheet("每100kg折算");
            var actualHeader = findRow(actual, "本次实验实际 kg");
            assertThat(actual.getRow(actualHeader + 1).getCell(4).getNumericCellValue()).isEqualTo(100d);
            assertThat(actual.getRow(actualHeader + 2).getCell(4).getNumericCellValue()).isEqualTo(1d);
            assertThat(actual.getRow(actualHeader + 3).getCell(4).getNumericCellValue()).isEqualTo(1d);
            assertThat(actual.getRow(actualHeader + 4).getCell(4).getNumericCellValue()).isEqualTo(102d);
            var normalizedHeader = findRow(normalized, "每100kg外部投入折算 kg");
            assertThat(normalized.getRow(normalizedHeader + 4).getCell(4).getNumericCellValue()).isEqualTo(100d);
            var allText = new StringBuilder();
            for (var sheet : List.of(actual, normalized)) for (var row : sheet) for (var cell : row) allText.append(cell.toString()).append('\n');
            assertThat(allText.toString()).contains(
                    "特级超长名称冷冻牛腩主料", "复合香辛料超长添加物名称", "工艺用水超长名称",
                    "1.1 预处理与复合调味", "2.1 冷却与包装")
                    .doesNotContain("调味牛腩\n", "熟制牛腩\n", "包装熟制牛腩");
        }
    }

    @Test
    void rendersMultiPageFormulaWithCorrectSectionContinuationHeadersAndCellSpacing() throws Exception {
        var firstMaterials = new java.util.ArrayList<ProcessPlan.StepMaterial>();
        var secondMaterials = new java.util.ArrayList<ProcessPlan.StepMaterial>();
        for (int index = 0; index < 30; index++) {
            var material = new ProcessPlan.StepMaterial(
                    "MAT-LONG-" + index, index + 1, index == 0 ? "PRIMARY" : "AUXILIARY",
                    "LONG-" + String.format("%02d", index + 1), "超长研发辅料名称" + String.format("%02d", index + 1) + "用于多页配方打印验证",
                    index % 2 == 0 ? "POWDER" : "LIQUID", index == 0 ? new BigDecimal("73") : BigDecimal.ONE,
                    "FORMULA-LONG-" + index, null, "EXTERNAL", null);
            (index < 15 ? firstMaterials : secondMaterials).add(material);
        }
        var intermediateId = "OUT-LONG-INTERMEDIATE";
        var preparation = new ProcessPlan.MinorStep(
                "STEP-LONG-1", 1, "PREP", "复合调味长名称原料预处理", "NORMAL", null, null, null,
                null, null, null, "混合机", "按本次试验记录混合", firstMaterials,
                List.of(new ProcessPlan.StepOutput(intermediateId, 1, "INTERMEDIATE", "复合调味中间物", "MARINATED", new BigDecimal("91"), true, true, null)), List.of());
        secondMaterials.add(0, new ProcessPlan.StepMaterial(
                "MAT-LONG-STEP", 1, "PRIMARY", null, "复合调味中间物", "MARINATED", new BigDecimal("91"),
                null, null, "STEP_OUTPUT", intermediateId));
        var finishing = new ProcessPlan.MinorStep(
                "STEP-LONG-2", 1, "FINISH", "长名称冷却包装与完成步骤", "NORMAL", null, null, null,
                null, null, null, "冷却包装线", "按本次试验记录完成", secondMaterials,
                List.of(new ProcessPlan.StepOutput("OUT-LONG-FINAL", 1, "FINISHED", "研发样品成品", "FINISHED", new BigDecimal("72"), true, false, null)), List.of());
        var plan = new ProcessPlan("PLAN-LONG-FORMULA", FORM_ID, 1, "DRAFT", List.of(
                new ProcessPlan.MajorProcess("MAJOR-LONG-1", 1, "PREP", "多辅料预处理工序", null, "PRIMARY_INPUT", null, List.of(preparation), List.of(), List.of(), null),
                new ProcessPlan.MajorProcess("MAJOR-LONG-2", 2, "FINISH", "冷却包装工序", null, "PRIMARY_INPUT", null, List.of(finishing), List.of(), List.of(), null)),
                new BigDecimal("72"), false);
        var view = new ProcessExportCheckService().view(
                "超长产品名称复合调味牛腩样品", "试验方案 多页配方 V8", plan, null, List.of())
                .withMetadata(new ProcessExportView.Metadata("1 kg 袋装", "研发张三", "2026-08-20"));

        var bytes = service.preview(view, ProcessArtifact.FORMULA_XLSX).content();
        var output = Path.of("target", "task-5-export-qa", "long-30-material-rnd-formula-v4.xlsx");
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.sheetIterator()).toIterable().extracting(org.apache.poi.ss.usermodel.Sheet::getSheetName)
                    .containsExactly("本次实际投料", "每100kg折算");
            var actual = workbook.getSheet("本次实际投料");
            var normalized = workbook.getSheet("每100kg折算");
            var actualHeader = findRow(actual, "本次实验实际 kg");
            var normalizedHeader = findRow(normalized, "每100kg外部投入折算 kg");
            assertThat(actual.getRepeatingRows().getFirstRow()).isEqualTo(actualHeader);
            assertThat(normalized.getRepeatingRows().getFirstRow()).isEqualTo(normalizedHeader);
            assertThat(actual.getRow(actualHeader + 1).getCell(4).getCellStyle().getIndention()).isEqualTo((short) 1);
            assertThat(actual.getRow(actualHeader + 1).getCell(5).getCellStyle().getIndention()).isEqualTo((short) 1);
            assertThat(actual.getRowBreaks()).isEmpty();
            assertThat(normalized.getRowBreaks()).isEmpty();
            for (var sheet : List.of(actual, normalized)) {
                assertThat(sheet.getRow(findRow(sheet, "外部原料实际总投入")).getCell(2).getStringCellValue()).isEqualTo("102 kg");
                assertThat(sheet.getFooter().getCenter()).contains("&P", "&N");
            }
        }
    }

    private int findRow(org.apache.poi.ss.usermodel.Sheet sheet, String value) {
        for (var row : sheet) for (var cell : row) if (value.equals(cell.toString())) return row.getRowNum();
        throw new AssertionError("Missing row value: " + value);
    }

    private List<Integer> findRows(org.apache.poi.ss.usermodel.Sheet sheet, String value) {
        var matches = new java.util.ArrayList<Integer>();
        for (var row : sheet) for (var cell : row) if (value.equals(cell.toString())) matches.add(row.getRowNum());
        return matches;
    }

    @Test
    void incrementsDocumentVersionsAndKeepsEarlierFilesDownloadable() {
        var revision = formalRevision();
        var first = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        var firstBytes = service.download(FORM_ID, revision.id(), first.id(), ENGINEER).content();
        var second = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);

        assertThat(first.documentVersion()).isEqualTo("1");
        assertThat(second.documentVersion()).isEqualTo("2");
        assertThat(service.list(FORM_ID, revision.id(), ENGINEER)).extracting(ProcessArtifact::id).containsExactly(second.id(), first.id());
        assertThat(service.download(FORM_ID, revision.id(), first.id(), ENGINEER).content()).isEqualTo(firstBytes);
    }

    @Test
    void preventsReadOrGenerateAcrossAnotherFormAndKeepsTesterReadOnly() {
        var revision = formalRevision();
        var tester = new SessionPrincipal("USER-TEST", "tester", "测试", "ou-test", "TESTER", null);

        assertThatThrownBy(() -> service.list(FORM_ID, revision.id(), tester))
                .isInstanceOf(com.lhr.rnd.api.BusinessException.class)
                .extracting(error -> ((com.lhr.rnd.api.BusinessException) error).code())
                .isEqualTo("PROCESS_REVISION_NOT_ASSIGNED");
        assertThatThrownBy(() -> service.generate(FORM_ID, revision.id(), ProcessArtifact.SOP_DOCX, tester))
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.list("FORM-OTHER", revision.id(), ENGINEER))
                .hasMessageContaining("无权");
    }

    @Test
    void serializesConcurrentGenerationsAndReportsMissingStoredContentWithoutReadingAPath() {
        var revision = formalRevision();
        var first = CompletableFuture.supplyAsync(() -> service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER));
        var second = CompletableFuture.supplyAsync(() -> service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER));
        var artifacts = List.of(first.join(), second.join());
        assertThat(artifacts).extracting(ProcessArtifact::documentVersion).containsExactlyInAnyOrder("1", "2");

        jdbc.update("update experiment_process_artifact set storage_key = ? where id = ?", "process-artifacts/missing.xlsx", artifacts.get(0).id());
        assertThatThrownBy(() -> service.download(FORM_ID, revision.id(), artifacts.get(0).id(), ENGINEER))
                .isInstanceOf(com.lhr.rnd.api.BusinessException.class)
                .extracting(error -> ((com.lhr.rnd.api.BusinessException) error).code())
                .isEqualTo("PROCESS_ARTIFACT_FILE_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where business_id = ? and action = ?", Integer.class,
                FORM_ID, "PROCESS_ARTIFACT_GENERATED")).isEqualTo(2);
    }

    @Test
    void rejectsTamperedOrTruncatedReadyBytesByDigestAndSize() throws Exception {
        var revision = formalRevision();
        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        Files.write(Path.of("target", "rnd-archive").resolve(artifact.storageKey()), new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER))
                .isInstanceOf(com.lhr.rnd.api.BusinessException.class)
                .extracting(error -> ((com.lhr.rnd.api.BusinessException) error).code())
                .isEqualTo("PROCESS_ARTIFACT_INTEGRITY_ERROR");
    }

    private com.lhr.rnd.model.ProcessRevision formalRevision() {
        var intermediateId = "OUT-ARTIFACT-" + UUID.randomUUID();
        var measurement = new ProcessPlan.ControlMeasurement("CM-ARTIFACT", 1, new BigDecimal("76"), "2026-08-19T22:00:00", "PASS", null, "复测合格哨兵", "实测正常哨兵");
        var control = new ProcessPlan.ControlPoint("CP-ARTIFACT", 1, "FOOD_SAFETY", "CRITICAL", "中心温度", new BigDecimal("75"), new BigDecimal("72"), new BigDecimal("85"), "℃", "探针测温", "数字探针", "每锅", "继续加热", true, "制品研发", "2026-08-19T22:05:00", "研发依据", List.of(measurement));
        var first = new ProcessPlan.MinorStep(null, 1, "MARINATE", "腌制", "NORMAL", "时间", "30", "min", null, null, null, "滚揉机", "均匀腌制", List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "鲜牛腩", "SOLID", new BigDecimal("10.0000"), "MAT-BEEF", "外部料备注哨兵", "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput(intermediateId, 1, "INTERMEDIATE", "腌制牛腩", "SEMI_SOLID", new BigDecimal("9.5000"), true, true, "流转熟制")), List.of());
        var second = new ProcessPlan.MinorStep(null, 2, "COOK", "熟制", "NORMAL", "温度", "85", "℃", null, null, null, "夹层锅", "加热至中心温度达标", List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", null, "腌制牛腩", "SEMI_SOLID", new BigDecimal("9.5000"), null, null, "STEP_OUTPUT", intermediateId)),
                List.of(new ProcessPlan.StepOutput("OUT-FINISHED", 1, "FINISHED", "熟制牛腩", "SEMI_SOLID", new BigDecimal("9.0000"), true, false, "成品产出备注哨兵")), List.of(control));
        var major = new ProcessPlan.MajorProcess(null, 1, "COOK", "熟制", "熟制描述哨兵", "PRIMARY_INPUT", "熟制备注哨兵", List.of(first, second), List.of(), List.of(), null);
        var current = planService.find(FORM_ID);
        var saved = planService.save(FORM_ID, new ProcessPlan(null, FORM_ID, current.versionNo(), "DRAFT", List.of(major), null, new BigDecimal("0.0100"), false));
        return revisionService.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(saved.versionNo(), true, "首次正式提交", ENGINEER.name()), ENGINEER);
    }

    private SopRevisionFixture derivedFormalRevisionForSop() {
        var outputId = "OUT-SOP-SOURCE-" + UUID.randomUUID();
        var measurement = new ProcessPlan.ControlMeasurement(
                "CM-SOP-UNIQUE", 1, new BigDecimal("79.9000"), "2026-08-19T22:00:22", "PASS",
                "测量偏差处理-SOP", "复测结果-SOP", "实测备注-SOP");
        var control = new ProcessPlan.ControlPoint(
                "CP-SOP-UNIQUE", 1, "FOOD_SAFETY", "CRITICAL", "中心温度控制项目-SOP",
                new BigDecimal("77.7000"), new BigDecimal("70.1000"), new BigDecimal("88.8000"), "℃-CONTROL-SOP",
                "探针检测方法-SOP", "数字探针工具-SOP", "每锅检测频次-SOP", "继续加热偏差处理-SOP",
                true, "追溯确认人-SOP", "2026-08-19T22:05:11", "研发依据-SOP-唯一", List.of(measurement));
        var first = new ProcessPlan.MinorStep(
                null, 1, "MARINATE-SOP", "腌制步骤-SOP", "NORMAL", "时长参数-SOP", "31.7", "min-SOP",
                "真空参数-SOP", "-0.08", "MPa-SOP", "滚揉设备-SOP-唯一", "均匀腌制操作要求-SOP-唯一",
                List.of(new ProcessPlan.StepMaterial(
                        null, 1, "PRIMARY", "BEEF-SOP-001", "外部鲜牛腩-SOP", "FROZEN-SOLID-SOP",
                        new BigDecimal("12.5000"), "MAT-BEEF-SOP-001", "外部投料备注-SOP", "EXTERNAL", null)),
                List.of(
                        new ProcessPlan.StepOutput(
                                outputId, 1, "INTERMEDIATE", "腌制中间产物-SOP", "MARINATED-STATE-SOP",
                                new BigDecimal("10.0000"), true, true, "中间产出备注-SOP"),
                        new ProcessPlan.StepOutput(
                                "OUT-SOP-EARLY-WASTE", 2, "WASTE", "前段修割损耗-SOP", "SOLID",
                                new BigDecimal("2.5000"), false, false, "前段旁路损耗-SOP")), List.of());
        var second = new ProcessPlan.MinorStep(
                null, 2, "COOK-SOP", "熟制步骤-SOP", "NORMAL", "中心温度参数-SOP", "88.8", "℃-SOP",
                null, null, null, "夹层锅设备-SOP-唯一", "加热至中心温度达标-SOP-唯一",
                List.of(new ProcessPlan.StepMaterial(
                        null, 1, "PRIMARY", null, "腌制中间产物-SOP", "MARINATED-STATE-SOP",
                        new BigDecimal("10.0000"), null, "中间投入备注-SOP", "STEP_OUTPUT", outputId)),
                List.of(new ProcessPlan.StepOutput(
                        "OUT-SOP-FINAL-UNIQUE", 1, "FINISHED", "最终熟制成品-SOP", "FINISHED-STATE-SOP",
                        new BigDecimal("8.0000"), true, false, "最终成品备注-SOP")), List.of(control));
        var major = new ProcessPlan.MajorProcess(
                null, 1, "HEAT-SOP", "热加工大工序-SOP", "大工序描述-SOP-唯一", "PRIMARY_INPUT", "大工序备注-SOP-唯一",
                List.of(first, second), List.of(), List.of(), null);
        var current = planService.find(FORM_ID);
        var saved = planService.save(FORM_ID, new ProcessPlan(
                null, FORM_ID, current.versionNo(), "DRAFT", List.of(major), null, new BigDecimal("0.0100"), false));
        var source = revisionService.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                saved.versionNo(), true, "SOP-REV1-首次原因唯一值", ENGINEER.name()), ENGINEER);
        var draft = revisionService.createDraftFromRevision(
                FORM_ID, source.id(), "SOP-REV2-变更原因唯一值", ENGINEER);
        var revision = revisionService.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(
                draft.versionNo(), true, null, ENGINEER.name()), ENGINEER);
        return new SopRevisionFixture(source.id(), revision, outputId);
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(com.lhr.rnd.api.BusinessException.class)
                .extracting(error -> ((com.lhr.rnd.api.BusinessException) error).code())
                .isEqualTo("PROCESS_PLAN_FORM_FORBIDDEN");
    }

    private com.lhr.rnd.model.ProcessRevision formalRevisionWithExternalWeights(List<BigDecimal> weights) {
        var suffix = UUID.randomUUID().toString();
        var materials = new java.util.ArrayList<ProcessPlan.StepMaterial>();
        for (int index = 0; index < weights.size(); index++) {
            materials.add(new ProcessPlan.StepMaterial(null, index + 1, index == 0 ? "PRIMARY" : "AUXILIARY",
                    Character.toString('A' + index), "物料" + index, "SOLID", weights.get(index), "MAT-" + suffix + "-" + index,
                    null, "EXTERNAL", null));
        }
        var total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var step = new ProcessPlan.MinorStep(null, 1, "MIX", "混合", "NORMAL", null, null, null, null, null, null,
                "混合机", "混合均匀", materials,
                List.of(new ProcessPlan.StepOutput("OUT-" + suffix, 1, "FINISHED", "成品", "SOLID", total, true, false, null)), List.of());
        var major = new ProcessPlan.MajorProcess(null, 1, "MIX", "混合", null, "PRIMARY_INPUT", "测试", List.of(step), List.of(), List.of(), null);
        var current = planService.find(FORM_ID);
        var saved = planService.save(FORM_ID, new ProcessPlan(null, FORM_ID, current.versionNo(), "DRAFT", List.of(major), null, new BigDecimal("0.0100"), false));
        return revisionService.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(saved.versionNo(), true, null, ENGINEER.name()), ENGINEER);
    }

    private String ledgerState(String storageKey) {
        return jdbc.queryForObject(
                "select state from process_artifact_cleanup_ledger where storage_key = ?", String.class, storageKey);
    }

    private int ledgerCount(String storageKey) {
        return jdbc.queryForObject(
                "select count(*) from process_artifact_cleanup_ledger where storage_key = ?", Integer.class, storageKey);
    }

    private void expire(String storageKey) {
        jdbc.update("update process_artifact_cleanup_ledger set lease_until = ? where storage_key = ?",
                LocalDateTime.now().minusMinutes(1), storageKey);
    }

    private ProcessArtifactCleanupLedgerService futureReconciler() {
        return new ProcessArtifactCleanupLedgerService(
                jdbc, storage, Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(2)), Duration.ofMillis(10));
    }

    private ProcessArtifactCleanupLedgerService futureReconciler(CountDownLatch started) {
        return new ProcessArtifactCleanupLedgerService(
                jdbc, storage, Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(2)), Duration.ofMillis(10)) {
            @Override public void reconcileOnStartup() {
                started.countDown();
                super.reconcileOnStartup();
            }
        };
    }

    private Path archivePath(String storageKey) {
        return Path.of("target", "rnd-archive").resolve(storageKey);
    }

    private record SopRevisionFixture(
            String sourceRevisionId,
            com.lhr.rnd.model.ProcessRevision revision,
            String outputId
    ) { }
}
