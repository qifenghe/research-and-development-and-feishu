package com.lhr.rnd.service;

import com.lhr.rnd.domain.ProcessRecipeService;
import com.lhr.rnd.model.ProcessArtifact;
import com.lhr.rnd.model.ProcessPlan;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProcessArtifactServiceTest {
    private static final String FORM_ID = "FORM-PROCESS-ARTIFACT";
    private static final SessionPrincipal ENGINEER = new SessionPrincipal("USER-ARTIFACT", "artifact", "制品研发", "ou-artifact", "RND_ENGINEER", null);

    @Autowired ProcessPlanService planService;
    @Autowired ProcessRevisionService revisionService;
    @Autowired ProcessArtifactService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedForm() {
        jdbc.update("delete from audit_log where business_id in (?, ?) ", FORM_ID, "FORM-PROCESS-ARTIFACT");
        jdbc.update("delete from experiment_process_artifact where process_revision_id in (select id from experiment_process_revision where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_revision where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", FORM_ID);
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, FORM_ID) == 0) {
            var now = LocalDateTime.now();
            jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)", "REQ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "客户", "1kg", "研发", "APPROVED", now);
            jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)", "PRJ-PROCESS-ARTIFACT", "REQ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
            jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)", "VER-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
            jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,assignee_name,created_at) values (?,?,?,?,?,?,?,?,?)", "TASK-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "VER-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "V1", "IN_PROGRESS", "制品研发", now);
            jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)", FORM_ID, "TASK-PROCESS-ARTIFACT", "PRJ-PROCESS-ARTIFACT", "VER-PROCESS-ARTIFACT", "S-PROCESS-ARTIFACT", "牛腩", "V1", "DRAFT", "研发", now);
        }
    }

    @Test
    void generatesAFormulaWorkbookFromOnlyAggregatedExternalMaterials() throws Exception {
        var revision = formalRevision();

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.FORMULA_XLSX, ENGINEER);
        var download = service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER);

        assertThat(artifact.status()).isEqualTo("READY");
        assertThat(download.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(download.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("物料编码");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("BEEF");
            assertThat(sheet.getRow(5).getCell(4).getNumericCellValue()).isEqualTo(10d);
            assertThat(sheet.getRow(5).getCell(6).getNumericCellValue()).isEqualTo(100d);
            assertThat(sheet.getRow(5).getCell(7).getStringCellValue()).contains("腌制");
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("合计");
            assertThat(sheet.getRow(6).getCell(6).getNumericCellValue()).isEqualTo(100d);
            assertThat(workbook.getSheetName(0)).doesNotContain("中间");
        }
    }

    @Test
    void generatesSopWithProductionControlsAndASeparateMeasurementTraceAppendix() throws Exception {
        var revision = formalRevision();

        var artifact = service.generate(FORM_ID, revision.id(), ProcessArtifact.SOP_DOCX, ENGINEER);
        var download = service.download(FORM_ID, revision.id(), artifact.id(), ENGINEER);

        assertThat(download.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        try (var document = new XWPFDocument(new ByteArrayInputStream(download.content()))) {
            var text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).collect(java.util.stream.Collectors.joining("\n"));
            var tableText = document.getTables().stream().flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream()).map(cell -> cell.getText()).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(text + tableText).contains("熟制", "腌制牛腩", "中间流转", "中心温度", "继续加热", "测量记录追溯附录", "数字探针", "研发依据");
            assertThat(text + tableText).contains("成品得率");
        }
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

        assertThat(service.list(FORM_ID, revision.id(), tester)).isEmpty();
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

    private com.lhr.rnd.model.ProcessRevision formalRevision() {
        var intermediateId = "OUT-ARTIFACT-" + UUID.randomUUID();
        var measurement = new ProcessPlan.ControlMeasurement("CM-ARTIFACT", 1, new BigDecimal("76"), "2026-08-19T22:00:00", "PASS", null, null, "实测正常");
        var control = new ProcessPlan.ControlPoint("CP-ARTIFACT", 1, "FOOD_SAFETY", "CRITICAL", "中心温度", new BigDecimal("75"), new BigDecimal("72"), new BigDecimal("85"), "℃", "探针测温", "数字探针", "每锅", "继续加热", true, "制品研发", "2026-08-19T22:05:00", "研发依据", List.of(measurement));
        var first = new ProcessPlan.MinorStep(null, 1, "MARINATE", "腌制", "NORMAL", "时间", "30", "min", null, null, null, "滚揉机", "均匀腌制", List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "鲜牛腩", "SOLID", new BigDecimal("10.0000"), "MAT-BEEF", null, "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput(intermediateId, 1, "INTERMEDIATE", "腌制牛腩", "SEMI_SOLID", new BigDecimal("9.5000"), true, true, "流转熟制")), List.of());
        var second = new ProcessPlan.MinorStep(null, 2, "COOK", "熟制", "NORMAL", "温度", "85", "℃", null, null, null, "夹层锅", "加热至中心温度达标", List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", null, "腌制牛腩", "SEMI_SOLID", new BigDecimal("9.5000"), null, null, "STEP_OUTPUT", intermediateId)),
                List.of(new ProcessPlan.StepOutput("OUT-FINISHED", 1, "FINISHED", "熟制牛腩", "SEMI_SOLID", new BigDecimal("9.0000"), true, false, null)), List.of(control));
        var major = new ProcessPlan.MajorProcess(null, 1, "COOK", "熟制", null, "PRIMARY_INPUT", "熟制损耗已记录", List.of(first, second), List.of(), List.of(), null);
        var current = planService.find(FORM_ID);
        var saved = planService.save(FORM_ID, new ProcessPlan(null, FORM_ID, current.versionNo(), "DRAFT", List.of(major), null, new BigDecimal("0.0100"), false));
        return revisionService.submit(FORM_ID, new ProcessRevisionService.SubmitCommand(saved.versionNo(), true, "首次正式提交", ENGINEER.name()), ENGINEER);
    }
}
