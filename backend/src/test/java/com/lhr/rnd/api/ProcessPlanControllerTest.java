package com.lhr.rnd.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessPlanControllerTest {
    private static final String FORM_ID = "FORM-PROCESS-CONTROLLER";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedForm() {
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", FORM_ID);
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, FORM_ID) > 0) return;
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "REQ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "客户", "1kg", "研发", "APPROVED", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "PRJ-PROCESS-CONTROLLER", "REQ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                "VER-PROCESS-CONTROLLER", "PRJ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)",
                "TASK-PROCESS-CONTROLLER", "PRJ-PROCESS-CONTROLLER", "VER-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "V1", "IN_PROGRESS", now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",
                FORM_ID, "TASK-PROCESS-CONTROLLER", "PRJ-PROCESS-CONTROLLER", "VER-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "V1", "DRAFT", "研发", now);
    }

    @Test
    void acceptsOldDraftJsonAndReturnsEmptyNewCollections() throws Exception {
        var oldDraft = """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"HEAT","processName":"热加工","yieldBasis":"PRIMARY_INPUT","steps":[{
                    "sequence":1,"stepCode":"CUT","stepName":"修割","stepType":"NORMAL","materials":[]
                  }],"inputs":[],"outputs":[]
                }]}""";

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(oldDraft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].outputs").isArray())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].outputs").isEmpty())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints").isArray())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints").isEmpty());
    }

    @Test
    void returnsAuthoritativeSubmissionCheckWithStableIssueCodes() throws Exception {
        var invalidDraft = """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"HEAT","processName":"热加工","yieldBasis":"PRIMARY_INPUT","steps":[],"inputs":[],"outputs":[]
                }]}""";
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidDraft))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/submission-check", FORM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.errors[*].code").value(hasItem("MAJOR_PRIMARY_INPUT_REQUIRED")));
    }
}
