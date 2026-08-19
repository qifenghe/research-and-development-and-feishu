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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProcessPlanServiceTest {
    @Autowired ProcessPlanService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedForm() {
        if (jdbc.queryForObject("select count(*) from experiment_form where id='FORM-PROCESS'", Integer.class) > 0) return;
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "REQ-PROCESS", "S-PROCESS", "牛腩", "预制菜", "客户", "1kg", "研发", "APPROVED", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "PRJ-PROCESS", "REQ-PROCESS", "S-PROCESS", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                "VER-PROCESS", "PRJ-PROCESS", "S-PROCESS", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)",
                "TASK-PROCESS", "PRJ-PROCESS", "VER-PROCESS", "S-PROCESS", "牛腩", "V1", "IN_PROGRESS", now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",
                "FORM-PROCESS", "TASK-PROCESS", "PRJ-PROCESS", "VER-PROCESS", "S-PROCESS", "牛腩", "V1", "DRAFT", "研发", now);
    }

    @Test
    void savesAndReloadsNestedPlanWithCalculatedYield() {
        var step = new ProcessPlan.MinorStep(null, 1, "BOIL", "煮制", "NORMAL", "温度", "95", "℃",
                "时间", "40", "min", "夹层锅", "保持微沸", List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "牛肉", "SOLID", new BigDecimal("10"), null, null,
                        "EXTERNAL", null),
                new ProcessPlan.StepMaterial(null, 2, "AUXILIARY", "SALT", "盐", "SOLID", new BigDecimal("0.2"), null, null,
                        "EXTERNAL", null)), List.of(
                new ProcessPlan.StepOutput(null, 1, "FINISHED", "熟制牛肉", "SEMI_SOLID", new BigDecimal("8"), true, false, null)),
                List.of(new ProcessPlan.ControlPoint(null, 1, "FOOD_SAFETY", "CRITICAL", "中心温度", new BigDecimal("75"),
                        new BigDecimal("75"), null, "℃", "探针测温", "每锅", "继续加热", true, "研发", List.of(
                        new ProcessPlan.ControlMeasurement(null, 1, new BigDecimal("76"), "2026-08-19T21:00:00", "PASS", null, null, null)))));
        var major = new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", "煮制与焖制", "PRIMARY_INPUT", null,
                List.of(step), List.of(new ProcessPlan.ProcessInput(null, 1, "PRIMARY", "BEEF", "牛肉", new BigDecimal("10"), null)),
                List.of(new ProcessPlan.ProcessOutput(null, 1, "QUALIFIED", new BigDecimal("8"), null)), null);

        var current = service.find("FORM-PROCESS");
        var saved = service.save("FORM-PROCESS", new ProcessPlan(null, "FORM-PROCESS", current.versionNo(), "DRAFT", List.of(major), null,
                new BigDecimal("0.0250"), false));
        var loaded = service.find("FORM-PROCESS");

        assertThat(saved.versionNo()).isPositive();
        assertThat(loaded.majorProcesses()).hasSize(1);
        assertThat(loaded.majorProcesses().get(0).steps()).hasSize(1);
        assertThat(loaded.majorProcesses().get(0).steps().get(0).materials()).hasSize(2);
        assertThat(loaded.majorProcesses().get(0).steps().get(0).materials().get(0).sourceType()).isEqualTo("EXTERNAL");
        assertThat(loaded.majorProcesses().get(0).steps().get(0).outputs()).singleElement()
                .satisfies(output -> assertThat(output.primaryOutput()).isTrue());
        assertThat(loaded.majorProcesses().get(0).steps().get(0).controlPoints()).singleElement()
                .satisfies(point -> {
                    assertThat(point.measurements()).hasSize(1);
                    assertThat(point.measurements().get(0).measuredAt()).isEqualTo("2026-08-19T21:00");
                });
        assertThat(loaded.majorProcesses().get(0).yield().mainYieldPercent()).isEqualByComparingTo("80.000000");
        assertThat(loaded.balanceToleranceKg()).isEqualByComparingTo("0.0250");
    }

    @Test
    void savesReloadsAndReplacesAReferencedStepOutputWithoutBreakingTheFlowLink() {
        var outputId = "FLOW-OUTPUT-1";
        var first = new ProcessPlan.MinorStep(null, 1, "CUT", "修割", "NORMAL", null, null, null, null, null, null,
                null, null, List.of(new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "鲜牛腩", "SOLID",
                new BigDecimal("10"), null, null, "EXTERNAL", null)), List.of(new ProcessPlan.StepOutput(outputId, 1,
                "INTERMEDIATE", "修割牛腩", "SOLID", new BigDecimal("10"), true, true, null)), List.of());
        var second = new ProcessPlan.MinorStep(null, 2, "COOK", "熟制", "NORMAL", null, null, null, null, null, null,
                null, null, List.of(new ProcessPlan.StepMaterial(null, 1, "PRIMARY", null, "修割牛腩", "SOLID",
                new BigDecimal("10"), null, null, "STEP_OUTPUT", outputId)), List.of(new ProcessPlan.StepOutput("FLOW-OUTPUT-2", 1,
                "FINISHED", "熟制牛腩", "SEMI_SOLID", new BigDecimal("8"), true, false, null)), List.of());
        var major = new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", null, "PRIMARY_INPUT", null,
                List.of(first, second), List.of(), List.of(), null);

        var current = service.find("FORM-PROCESS");
        var saved = service.save("FORM-PROCESS", new ProcessPlan(null, "FORM-PROCESS", current.versionNo(), "DRAFT", List.of(major), null, false));
        var reloaded = service.find("FORM-PROCESS");
        var secondMaterial = reloaded.majorProcesses().get(0).steps().get(1).materials().get(0);

        assertThat(secondMaterial.sourceType()).isEqualTo("STEP_OUTPUT");
        assertThat(secondMaterial.sourceStepOutputId()).isEqualTo(outputId);
        var replaced = service.save("FORM-PROCESS", new ProcessPlan(reloaded.id(), "FORM-PROCESS", saved.versionNo(), "DRAFT",
                reloaded.majorProcesses(), null, false));
        assertThat(replaced.majorProcesses().get(0).steps().get(1).materials().get(0).sourceStepOutputId()).isEqualTo(outputId);
    }

    @Test
    void rejectsSavingMoreThanOnePrimaryMaterialInAStep() {
        var invalidStep = new ProcessPlan.MinorStep(null, 1, "CUT", "修割", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(
                new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "牛肉", "SOLID", new BigDecimal("10"), null, null,
                        "EXTERNAL", null),
                new ProcessPlan.StepMaterial(null, 2, "PRIMARY", "PORK", "猪肉", "SOLID", new BigDecimal("2"), null, null,
                        "EXTERNAL", null)), List.of(), List.of());
        var major = new ProcessPlan.MajorProcess(null, 1, "CUT", "修割", null, "PRIMARY_INPUT", null,
                List.of(invalidStep), List.of(), List.of(), null);
        var current = service.find("FORM-PROCESS");

        assertThatThrownBy(() -> service.save("FORM-PROCESS", new ProcessPlan(null, "FORM-PROCESS", current.versionNo(), "DRAFT",
                List.of(major), null, false))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one primary material");
    }

    @Test
    void rejectsStaleVersion() {
        var current = service.find("FORM-PROCESS");
        assertThatThrownBy(() -> service.save("FORM-PROCESS",
                new ProcessPlan(null, "FORM-PROCESS", current.versionNo() + 5, "DRAFT", List.of(), null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
    }
}
