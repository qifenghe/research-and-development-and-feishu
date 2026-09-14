package com.lhr.rnd.service;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TrialSchemeServiceTest {
    private static final String FORM_ID = "FORM-TRIAL-SERVICE";
    private static final String TASK_ID = "TASK-TRIAL-SERVICE";
    private static final SessionPrincipal OWNER = new SessionPrincipal(
            "USER-TRIAL-OWNER", "trial_owner", "方案研发", null, "RND_ENGINEER", null);
    private static final SessionPrincipal OTHER = new SessionPrincipal(
            "USER-TRIAL-OTHER", "trial_other", "其他研发", null, "RND_ENGINEER", null);
    private static final SessionPrincipal DIRECTOR = new SessionPrincipal(
            "USER-TRIAL-DIRECTOR", "trial_director", "研发总监", null, "RND_DIRECTOR", null);
    private static final SessionPrincipal TESTER = new SessionPrincipal(
            "USER-TRIAL-TESTER", "trial_tester", "测试人员", null, "TESTER", null);

    @Autowired TrialSchemeService service;
    @Autowired RolePermissionService permissions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedForm() {
        jdbc.update("delete from experiment_trial_scheme where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_form where id = ?", FORM_ID);
        jdbc.update("delete from rnd_task where id = ?", TASK_ID);
        jdbc.update("delete from sample_version where id = 'VER-TRIAL-SERVICE'");
        jdbc.update("delete from sample_project where id = 'PRJ-TRIAL-SERVICE'");
        jdbc.update("delete from sample_request where id = 'REQ-TRIAL-SERVICE'");
        for (var principal : List.of(OWNER, OTHER, DIRECTOR, TESTER)) {
            jdbc.update("delete from user_account where id = ?", principal.userId());
            jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,'ACTIVE',current_timestamp,current_timestamp)",
                    principal.userId(), principal.username(), "x", principal.name(), principal.role());
        }
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "REQ-TRIAL-SERVICE", "S-TRIAL-SERVICE", "牛腩", "预制菜", "客户", "1kg", "研发", "PENDING_ASSIGNMENT", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "PRJ-TRIAL-SERVICE", "REQ-TRIAL-SERVICE", "S-TRIAL-SERVICE", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                "VER-TRIAL-SERVICE", "PRJ-TRIAL-SERVICE", "S-TRIAL-SERVICE", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,assignee_name,assignee_user_id,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                TASK_ID, "PRJ-TRIAL-SERVICE", "VER-TRIAL-SERVICE", "S-TRIAL-SERVICE", "牛腩", "V1", "SAMPLING", OWNER.name(), OWNER.userId(), now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",
                FORM_ID, TASK_ID, "PRJ-TRIAL-SERVICE", "VER-TRIAL-SERVICE", "S-TRIAL-SERVICE", "牛腩", "V1", "DRAFT", OWNER.name(), now);
    }

    @Test
    void persistsIndependentTrialsAndUsesAtomicVersionedSaves() {
        var a = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "方案A", "降低损耗", "熟制时间", graph("A", "10.2"), planned("A", "10.0")), OWNER);
        var b = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "方案B", "优化口感", "蒸煮温度", graph("B", "11.0"), planned("B", "10.5")), OWNER);

        var savedB = service.save(FORM_ID, b.id(), new TrialSchemeService.SaveCommand(
                b.versionNo(), "方案B-改", b.purpose(), b.variables(), TrialScheme.Conclusion.RECOMMEND,
                "口感稳定", new BigDecimal("9.5"), "弹性好", TrialScheme.Difficulty.MEDIUM,
                b.plan(), b.plannedData()), OWNER);

        assertThat(service.find(FORM_ID, a.id(), OWNER).name()).isEqualTo("方案A");
        assertThat(service.find(FORM_ID, savedB.id(), OWNER).versionNo()).isEqualTo(2);
        assertThat(service.list(FORM_ID, OWNER)).hasSize(2);
        assertThatThrownBy(() -> service.save(FORM_ID, b.id(), new TrialSchemeService.SaveCommand(
                1, "过期保存", null, null, TrialScheme.Conclusion.PENDING,
                null, null, null, null, b.plan(), b.plannedData()), OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_VERSION_CONFLICT");
    }

    @Test
    void assignsStableServerIdsAndRejectsDanglingPlannedOrFlowReferences() {
        var created = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "服务端ID", null, null, graph(null, "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var major = created.plan().majorProcesses().get(0);
        var step = major.steps().get(0);
        var material = step.materials().get(0);

        assertThat(major.id()).startsWith("TMAJ-");
        assertThat(step.id()).startsWith("TSTEP-");
        assertThat(material.id()).startsWith("TMAT-");
        assertThat(created.majorOrigins()).containsEntry(major.id(), major.id());

        var badPlanned = new TrialScheme.PlannedData(Map.of("FOREIGN-MATERIAL", BigDecimal.ONE), Map.of(), Map.of(), null, null);
        assertThatThrownBy(() -> service.save(FORM_ID, created.id(), new TrialSchemeService.SaveCommand(
                created.versionNo(), created.name(), null, null, TrialScheme.Conclusion.PENDING,
                null, null, null, null, created.plan(), badPlanned), OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_GRAPH_REFERENCE_INVALID");

        var badMaterial = new ProcessPlan.StepMaterial(null, 1, "MAIN", null, "牛腩", "RAW", BigDecimal.ONE,
                null, null, "STEP_OUTPUT", "FOREIGN-OUTPUT");
        var badPlan = replaceMaterials(created.plan(), List.of(badMaterial));
        assertThatThrownBy(() -> service.save(FORM_ID, created.id(), new TrialSchemeService.SaveCommand(
                created.versionNo(), created.name(), null, null, TrialScheme.Conclusion.PENDING,
                null, null, null, null, badPlan, TrialScheme.PlannedData.empty()), OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_GRAPH_REFERENCE_INVALID");
    }

    @Test
    void rejectsMalformedEvaluationAndClearsAllUntrustedConfirmationsOnSave() {
        var created = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "安全保存", null, null, graph("SAFE", "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var forged = withForgedConfirmation(created.plan());

        var saved = service.save(FORM_ID, created.id(), new TrialSchemeService.SaveCommand(
                created.versionNo(), "安全保存", null, null, TrialScheme.Conclusion.ADJUST,
                null, new BigDecimal("9.9"), null, TrialScheme.Difficulty.EASY, forged, created.plannedData()), OWNER);

        assertThatThrownBy(() -> service.save(FORM_ID, created.id(), new TrialSchemeService.SaveCommand(
                created.versionNo(), "安全保存", null, null, TrialScheme.Conclusion.PENDING,
                null, new BigDecimal("-0.1"), null, null, created.plan(), created.plannedData()), OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_QUALITY_SCORE_INVALID");
        var point = saved.plan().majorProcesses().get(0).steps().get(0).controlPoints().get(0);
        assertThat(point.confirmedBy()).isNull();
        assertThat(point.confirmedAt()).isNull();
        assertThat(point.resolved()).isFalse();
        assertThat(point.measurements().get(0).retestResult()).isNull();
    }

    @Test
    void persistsCopyLineageAndServerOwnedInheritedMeasurementProvenance() {
        var source = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "来源方案", "验证熟制", "时间", graph("COPY", "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var copied = service.copy(FORM_ID, source.id(), new TrialSchemeService.CopyCommand(
                source.versionNo(), "带实测副本", true), OWNER);
        var sourceMajor = source.plan().majorProcesses().get(0);
        var copiedMajor = copied.plan().majorProcesses().get(0);
        var copiedMeasurement = copiedMajor.steps().get(0).controlPoints().get(0).measurements().get(0);

        assertThat(copied.sourceTrialId()).isEqualTo(source.id());
        assertThat(copied.inheritedActuals()).isTrue();
        assertThat(copiedMajor.id()).isNotEqualTo(sourceMajor.id());
        assertThat(copied.majorOrigins().get(copiedMajor.id())).isEqualTo(source.majorOrigins().get(sourceMajor.id()));
        assertThat(jdbc.queryForObject("select inherited_measurement_ids_json from experiment_trial_scheme where id = ?", String.class, copied.id()))
                .contains(copiedMeasurement.id());
        assertThat(service.inheritedMeasurementIds(FORM_ID, copied.id(), OWNER)).containsExactly(copiedMeasurement.id());

        var saved = service.save(FORM_ID, copied.id(), new TrialSchemeService.SaveCommand(
                copied.versionNo(), "带实测副本-改", copied.purpose(), copied.variables(), copied.conclusion(), null,
                null, null, null, copied.plan(), copied.plannedData()), OWNER);
        assertThat(saved.inheritedActuals()).isTrue();
        assertThat(jdbc.queryForObject("select inherited_measurement_ids_json from experiment_trial_scheme where id = ?", String.class, copied.id()))
                .contains(copiedMeasurement.id());
        assertThat(service.find(FORM_ID, source.id(), OWNER).name()).isEqualTo("来源方案");
    }

    @Test
    void initializesIndependentServerMajorOriginsForRepeatedClientKeysAndCopiesThemTransitively() {
        var first = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "共享UI键-A", null, null, graph("SHARED-KEY", "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var second = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "共享UI键-B", null, null, graph("SHARED-KEY", "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var firstMajorId = first.plan().majorProcesses().get(0).id();
        var secondMajorId = second.plan().majorProcesses().get(0).id();
        var copied = service.copy(FORM_ID, first.id(), new TrialSchemeService.CopyCommand(first.versionNo(), "A副本", false), OWNER);
        var copiedMajorId = copied.plan().majorProcesses().get(0).id();

        assertThat(first.majorOrigins()).containsEntry(firstMajorId, firstMajorId);
        assertThat(second.majorOrigins()).containsEntry(secondMajorId, secondMajorId);
        assertThat(first.majorOrigins().get(firstMajorId)).isNotEqualTo(second.majorOrigins().get(secondMajorId));
        assertThat(copied.majorOrigins()).containsEntry(copiedMajorId, firstMajorId);
    }

    @Test
    void classifiesInheritedMeasurementsByImmutableObservationFingerprintAfterClientRekeysIds() {
        var source = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "指纹来源", null, null, graph("FINGERPRINT", "10.2"), TrialScheme.PlannedData.empty()), OWNER);
        var copied = service.copy(FORM_ID, source.id(), new TrialSchemeService.CopyCommand(
                source.versionNo(), "指纹副本", true), OWNER);
        var fingerprints = service.inheritedMeasurementFingerprints(FORM_ID, copied.id(), OWNER);
        var rekeyedPlan = withMeasurement(copied.plan(), "CLIENT-REKEYED-MEASUREMENT", new BigDecimal("80"),
                "2026-09-15T10:00:00.000", "PASS");
        var rekeyed = service.save(FORM_ID, copied.id(), new TrialSchemeService.SaveCommand(
                copied.versionNo(), copied.name(), null, null, copied.conclusion(), null, null, null, null,
                rekeyedPlan, copied.plannedData()), OWNER);
        var rekeyedMeasurementId = rekeyed.plan().majorProcesses().get(0).steps().get(0).controlPoints().get(0).measurements().get(0).id();

        assertThat(rekeyedMeasurementId).isNotEqualTo("CLIENT-REKEYED-MEASUREMENT");
        assertThat(service.classifyInheritedMeasurements(FORM_ID, copied.id(), rekeyed.plan(), OWNER))
                .containsEntry(rekeyedMeasurementId, true);
        assertThat(service.inheritedMeasurementFingerprints(FORM_ID, copied.id(), OWNER)).isEqualTo(fingerprints);

        var reinterpretedPlan = withMeasurement(rekeyed.plan(), rekeyedMeasurementId, new BigDecimal("80"), "FAIL");
        var reinterpreted = service.save(FORM_ID, copied.id(), new TrialSchemeService.SaveCommand(
                rekeyed.versionNo(), copied.name(), null, null, copied.conclusion(), null, null, null, null,
                reinterpretedPlan, copied.plannedData()), OWNER);
        assertThat(service.classifyInheritedMeasurements(FORM_ID, copied.id(), reinterpreted.plan(), OWNER))
                .containsEntry(rekeyedMeasurementId, true);

        var changedPlan = withMeasurement(reinterpreted.plan(), rekeyedMeasurementId, new BigDecimal("80"),
                "2026-09-15T10:00:00.001", "PASS");
        var changed = service.save(FORM_ID, copied.id(), new TrialSchemeService.SaveCommand(
                reinterpreted.versionNo(), copied.name(), null, null, copied.conclusion(), null, null, null, null,
                changedPlan, copied.plannedData()), OWNER);
        assertThat(service.classifyInheritedMeasurements(FORM_ID, copied.id(), changed.plan(), OWNER))
                .containsEntry(rekeyedMeasurementId, false);
        assertThat(service.inheritedMeasurementFingerprints(FORM_ID, copied.id(), OWNER)).isEqualTo(fingerprints);
    }

    @Test
    void enforcesOwnerRolesAndDraftLifecycleForEveryMutation() {
        var created = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "权限方案", null, null, graph("AUTH", "10.2"), TrialScheme.PlannedData.empty()), OWNER);

        for (var unauthorized : List.of(OTHER, TESTER)) {
            assertThatThrownBy(() -> service.find(FORM_ID, created.id(), unauthorized))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.save(FORM_ID, created.id(), saveOf(created), unauthorized))
                    .isInstanceOf(BusinessException.class);
        }
        jdbc.update("update rnd_task set assignee_user_id = null, assignee_name = null where id = ?", TASK_ID);
        assertThatThrownBy(() -> service.list(FORM_ID, OWNER)).isInstanceOf(BusinessException.class);
        jdbc.update("update rnd_task set assignee_user_id = ?, assignee_name = ? where id = ?", OWNER.userId(), OWNER.name(), TASK_ID);
        jdbc.update("update experiment_form set status = 'LOCKED' where id = ?", FORM_ID);

        assertThatThrownBy(() -> service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "锁定", null, null, graph("LOCK", "1"), TrialScheme.PlannedData.empty()), OWNER))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_FORM_LOCKED");
        assertThatThrownBy(() -> service.save(FORM_ID, created.id(), saveOf(created), OWNER))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_FORM_LOCKED");
        assertThatThrownBy(() -> service.copy(FORM_ID, created.id(), new TrialSchemeService.CopyCommand(created.versionNo(), "副本", false), OWNER))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_FORM_LOCKED");
        assertThatThrownBy(() -> service.archive(FORM_ID, created.id(), new TrialSchemeService.ArchiveCommand(created.versionNo(), true), OWNER))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo("TRIAL_FORM_LOCKED");
    }

    @Test
    void directorStillGetsNotFoundForAMissingFormAndArchiveIsReversibleAndVersioned() {
        assertThatThrownBy(() -> service.list("FORM-NOT-FOUND", DIRECTOR))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("EXPERIMENT_FORM_NOT_FOUND");
        var created = service.create(FORM_ID, new TrialSchemeService.CreateCommand(
                "归档方案", null, null, graph("ARC", "1"), TrialScheme.PlannedData.empty()), DIRECTOR);
        var archived = service.archive(FORM_ID, created.id(), new TrialSchemeService.ArchiveCommand(created.versionNo(), true), DIRECTOR);
        var restored = service.archive(FORM_ID, created.id(), new TrialSchemeService.ArchiveCommand(archived.versionNo(), false), DIRECTOR);

        assertThat(archived.archived()).isTrue();
        assertThat(restored.archived()).isFalse();
        assertThat(restored.versionNo()).isEqualTo(3);
    }

    @Test
    void rolePermissionsExposeOnlyTheTrialRoutesNeededByRndEditors() {
        assertThat(permissions.hasPermission("RND_ENGINEER", "GET", "/api/v1/experiment-forms/F1/trials")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "GET", "/api/v1/experiment-forms/F1/trials/T1")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "POST", "/api/v1/experiment-forms/F1/trials")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "POST", "/api/v1/experiment-forms/F1/trials/T1/copy")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "POST", "/api/v1/experiment-forms/F1/trials/T1/archive")).isTrue();
        assertThat(permissions.hasPermission("RND_ENGINEER", "PUT", "/api/v1/experiment-forms/F1/trials/T1")).isTrue();
        assertThat(permissions.hasPermission("RND_DIRECTOR", "GET", "/api/v1/experiment-forms/F1/trials")).isTrue();
        assertThat(permissions.hasPermission("TESTER", "GET", "/api/v1/experiment-forms/F1/trials")).isFalse();
        assertThat(permissions.hasPermission("FINANCE", "GET", "/api/v1/experiment-forms/F1/trials/T1")).isFalse();
    }

    private TrialSchemeService.SaveCommand saveOf(TrialScheme trial) {
        return new TrialSchemeService.SaveCommand(trial.versionNo(), trial.name(), trial.purpose(), trial.variables(), trial.conclusion(),
                trial.recommendationReason(), trial.qualityScore(), trial.qualityNotes(), trial.difficulty(), trial.plan(), trial.plannedData());
    }

    private TrialScheme.PlannedData planned(String suffix, String weight) {
        return new TrialScheme.PlannedData(Map.of("MAT-" + suffix, new BigDecimal(weight)), Map.of(), Map.of(), null, "实测口径");
    }

    private ProcessPlan graph(String suffix, String weight) {
        String idSuffix = suffix == null ? null : "-" + suffix;
        var measurement = new ProcessPlan.ControlMeasurement(idSuffix == null ? null : "MEASURE" + idSuffix, 1,
                new BigDecimal("80"), "2026-09-15T10:00:00", "PASS", null, null, null);
        var point = new ProcessPlan.ControlPoint(idSuffix == null ? null : "POINT" + idSuffix, 1, "TEMPERATURE", "CRITICAL", "中心温度",
                new BigDecimal("80"), new BigDecimal("75"), new BigDecimal("90"), "℃", "探针", "温度计", "每批", "复测", false, null, null, null, List.of(measurement));
        var material = new ProcessPlan.StepMaterial(idSuffix == null ? null : "MAT" + idSuffix, 1, "MAIN", "M1", "牛腩", "RAW",
                new BigDecimal(weight), null, null, "EXTERNAL", null);
        var output = new ProcessPlan.StepOutput(idSuffix == null ? null : "OUT" + idSuffix, 1, "MAIN", "熟制牛腩", "COOKED",
                new BigDecimal("9"), true, true, null);
        var step = new ProcessPlan.MinorStep(idSuffix == null ? null : "STEP" + idSuffix, 1, "S1", "熟制", "NORMAL",
                "时间", "30", "min", "温度", "95", "℃", "蒸箱", "熟制", List.of(material), List.of(output), List.of(point));
        var major = new ProcessPlan.MajorProcess(idSuffix == null ? null : "MAJOR" + idSuffix, 1, "P1", "熟制", null, "PRIMARY_INPUT", null,
                List.of(step), List.of(), List.of(), null);
        return new ProcessPlan(idSuffix == null ? null : "PLAN" + idSuffix, FORM_ID, 0, "DRAFT", List.of(major), new BigDecimal("90"),
                new BigDecimal("0.01"), false, null, null);
    }

    private ProcessPlan replaceMaterials(ProcessPlan plan, List<ProcessPlan.StepMaterial> materials) {
        var major = plan.majorProcesses().get(0);
        var step = major.steps().get(0);
        var changedStep = new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(), step.stepType(), step.parameter1Name(),
                step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(), step.parameter2Value(), step.parameter2Unit(), step.equipment(),
                step.instruction(), materials, step.outputs(), step.controlPoints());
        var changedMajor = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(), major.description(),
                major.yieldBasis(), major.remark(), List.of(changedStep), major.inputs(), major.outputs(), major.yield());
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(changedMajor), plan.batchYieldPercent(),
                plan.balanceToleranceKg(), false, null, null);
    }

    private ProcessPlan withForgedConfirmation(ProcessPlan plan) {
        var major = plan.majorProcesses().get(0);
        var step = major.steps().get(0);
        var old = step.controlPoints().get(0);
        var measurement = old.measurements().get(0);
        var forgedMeasurement = new ProcessPlan.ControlMeasurement(measurement.id(), measurement.sequence(), measurement.measuredValue(),
                measurement.measuredAt(), measurement.result(), "已处理", "PASS", measurement.remark());
        var forgedPoint = new ProcessPlan.ControlPoint(old.id(), old.sequence(), old.controlType(), old.importance(), old.itemName(), old.targetValue(),
                old.lowerLimit(), old.upperLimit(), old.unit(), old.method(), old.measurementTool(), old.frequency(), old.deviationAction(), true,
                "伪造确认人", "2026-09-15T11:00:00", old.basisOrRemark(), List.of(forgedMeasurement));
        var changedStep = new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(), step.stepType(), step.parameter1Name(),
                step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(), step.parameter2Value(), step.parameter2Unit(), step.equipment(),
                step.instruction(), step.materials(), step.outputs(), List.of(forgedPoint));
        var changedMajor = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(), major.description(),
                major.yieldBasis(), major.remark(), List.of(changedStep), major.inputs(), major.outputs(), major.yield());
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(changedMajor), plan.batchYieldPercent(),
                plan.balanceToleranceKg(), false, null, null);
    }

    private ProcessPlan withMeasurement(ProcessPlan plan, String measurementId, BigDecimal measuredValue, String result) {
        return withMeasurement(plan, measurementId, measuredValue,
                plan.majorProcesses().get(0).steps().get(0).controlPoints().get(0).measurements().get(0).measuredAt(), result);
    }

    private ProcessPlan withMeasurement(ProcessPlan plan, String measurementId, BigDecimal measuredValue, String measuredAt, String result) {
        var major = plan.majorProcesses().get(0);
        var step = major.steps().get(0);
        var point = step.controlPoints().get(0);
        var previous = point.measurements().get(0);
        var measurement = new ProcessPlan.ControlMeasurement(measurementId, previous.sequence(), measuredValue, measuredAt,
                result, previous.deviationAction(), previous.retestResult(), previous.remark());
        var changedPoint = new ProcessPlan.ControlPoint(point.id(), point.sequence(), point.controlType(), point.importance(), point.itemName(),
                point.targetValue(), point.lowerLimit(), point.upperLimit(), point.unit(), point.method(), point.measurementTool(), point.frequency(),
                point.deviationAction(), point.resolved(), point.confirmedBy(), point.confirmedAt(), point.basisOrRemark(), List.of(measurement));
        var changedStep = new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(), step.stepType(), step.parameter1Name(),
                step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(), step.parameter2Value(), step.parameter2Unit(), step.equipment(),
                step.instruction(), step.materials(), step.outputs(), List.of(changedPoint));
        var changedMajor = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(), major.description(),
                major.yieldBasis(), major.remark(), List.of(changedStep), major.inputs(), major.outputs(), major.yield());
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), List.of(changedMajor), plan.batchYieldPercent(),
                plan.balanceToleranceKg(), false, null, null);
    }
}
