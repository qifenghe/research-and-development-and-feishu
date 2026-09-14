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

import com.lhr.rnd.service.SessionPrincipal;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        var accountNow = LocalDateTime.now();
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) select ?,?,?,?,?,?,?,? where not exists (select 1 from user_account where id = ?)",
                "USER-PROCESS", "rnd_engineer_process", "x", "会话研发", "RND_ENGINEER", "ACTIVE", accountNow, accountNow, "USER-PROCESS");
        jdbc.update("delete from audit_log where business_type = ? and business_id = ?", "PROCESS_CONTROL_POINT", "CP-DEVIATION");
        jdbc.update("delete from test_assignment where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_process_artifact where process_revision_id in (select id from experiment_process_revision where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_revision where experiment_form_id = ?", FORM_ID);
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", FORM_ID);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", FORM_ID);
        if (jdbc.queryForObject("select count(*) from experiment_form where id = ?", Integer.class, FORM_ID) > 0) {
            jdbc.update("update rnd_task set assignee_name = ?, assignee_user_id = ? where id = ?", "会话研发", "USER-PROCESS", "TASK-PROCESS-CONTROLLER");
            return;
        }
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "REQ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "客户", "1kg", "研发", "PENDING_ASSIGNMENT", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "PRJ-PROCESS-CONTROLLER", "REQ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                "VER-PROCESS-CONTROLLER", "PRJ-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)",
                "TASK-PROCESS-CONTROLLER", "PRJ-PROCESS-CONTROLLER", "VER-PROCESS-CONTROLLER", "S-PROCESS-CONTROLLER", "牛腩", "V1", "SAMPLING", now);
        jdbc.update("update rnd_task set assignee_name = ?, assignee_user_id = ? where id = ?", "会话研发", "USER-PROCESS", "TASK-PROCESS-CONTROLLER");
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
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
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
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(invalidDraft))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/submission-check", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.errors[*].code").value(hasItem("MAJOR_PRIMARY_INPUT_REQUIRED")));
    }

    @Test
    void roundTripsControlPointTraceabilityFieldsAddedAfterTheOriginalDraftShape() throws Exception {
        var draftWithTraceability = """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"HEAT","processName":"热加工","yieldBasis":"PRIMARY_INPUT","steps":[{
                    "sequence":1,"stepCode":"COOK","stepName":"熟制","stepType":"NORMAL","materials":[],"outputs":[],"controlPoints":[{
                      "id":"CP-TRACEABILITY","sequence":1,"controlType":"FOOD_SAFETY","importance":"NORMAL","itemName":"中心温度",
                      "targetValue":75,"lowerLimit":72,"upperLimit":85,"unit":"℃","method":"探针测温","measurementTool":"数字探针",
                      "frequency":"每锅","deviationAction":"继续加热","resolved":true,"confirmedBy":"研发","confirmedAt":"2026-08-19T21:15:00",
                      "basisOrRemark":"以产品中心温度为放行依据","measurements":[]
                    }]
                  }],"inputs":[],"outputs":[]
                }]}""";

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(draftWithTraceability))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].measurementTool").value("数字探针"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].basisOrRemark").value("以产品中心温度为放行依据"));
    }

    @Test
    void rejectsClientForgedDeviationConfirmationAndAuditsDirectorConfirmation() throws Exception {
        var draft = """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"HEAT","processName":"热加工","yieldBasis":"PRIMARY_INPUT","remark":"损耗已说明","steps":[{
                    "sequence":1,"stepCode":"COOK","stepName":"熟制","stepType":"NORMAL",
                    "materials":[{"sequence":1,"materialRole":"PRIMARY","materialName":"牛肉","materialState":"SOLID","weightKg":10,"formulaMaterialId":"BEEF","sourceType":"EXTERNAL"}],
                    "outputs":[{"id":"OUT-DEVIATION","sequence":1,"outputType":"FINISHED","outputName":"熟制牛肉","materialState":"SOLID","weightKg":8,"primaryOutput":true,"continueFlow":false}],
                    "controlPoints":[{"id":"CP-DEVIATION","sequence":1,"controlType":"FOOD_SAFETY","importance":"CRITICAL","itemName":"中心温度","lowerLimit":75,
                      "resolved":true,"confirmedBy":"伪造总监","confirmedAt":"2026-08-19T21:15:00","basisOrRemark":"复测合格","measurements":[{"id":"M-DEVIATION","sequence":1,"measuredValue":72,"result":"FAIL","deviationAction":"继续加热","retestResult":"PASS"}]}]
                  }],"inputs":[],"outputs":[]
                }]}""";

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist());

        var engineerAttempt = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_DEVIATION_CONFIRMATION_FORBIDDEN"));

        var director = new SessionPrincipal("USER-DIRECTOR", "rnd_director", "研发总监", null, "RND_DIRECTOR", Instant.now().plusSeconds(60));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, director)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(true))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").value("研发总监"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedAt").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where business_type='PROCESS_CONTROL_POINT' and business_id='CP-DEVIATION' and action='CRITICAL_DEVIATION_CONFIRMED' and operator_user_id='USER-DIRECTOR'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void preservesOnlyThePersistedDirectorConfirmationWhenAnUnchangedDeviationIsSavedAgain() throws Exception {
        var draft = criticalDeviationDraft("记录第一次打样");
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isOk());

        var director = new SessionPrincipal("USER-DIRECTOR", "rnd_director", "研发总监", null, "RND_DIRECTOR", Instant.now().plusSeconds(60));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, director)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isOk());

        var majorId = currentMajorId();
        var stepId = currentStepId();

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(criticalDeviationDraftWithLocation("只改工艺说明", 1, majorId, stepId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(true))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").value("研发总监"));

        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":2,\"confirmed\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void dropsDirectorConfirmationWhenTheIncomingMajorIdIsMissingEvenIfTheStepIdMatches() throws Exception {
        saveAndConfirmCriticalDeviation();
        var stepId = currentStepId();

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalDeviationDraftWithLocation("同路径但未带大工序身份", 1, null, stepId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist());
    }

    @Test
    void dropsDirectorConfirmationWhenTheIncomingStepIdIsMissingEvenIfTheMajorIdMatches() throws Exception {
        saveAndConfirmCriticalDeviation();
        var majorId = currentMajorId();

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalDeviationDraftWithLocation("同路径但未带小步骤身份", 1, majorId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist());
    }

    @Test
    void dropsPersistedDirectorConfirmationWhenTheMeasurementSetChanges() throws Exception {
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(criticalDeviationDraft("首次")))
                .andExpect(status().isOk());
        var director = new SessionPrincipal("USER-DIRECTOR", "rnd_director", "研发总监", null, "RND_DIRECTOR", Instant.now().plusSeconds(60));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, director)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isOk());

        var changedMeasurement = criticalDeviationDraft("首次")
                .replace("\"versionNo\":0", "\"versionNo\":1")
                .replace("\"measuredValue\":72", "\"measuredValue\":71");
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(changedMeasurement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist());
    }

    @Test
    void doesNotCarryDirectorConfirmationWhenAControlPointIdIsReusedInAnotherMajorAndStep() throws Exception {
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(criticalDeviationDraft("首次熟制")))
                .andExpect(status().isOk());
        var director = new SessionPrincipal("USER-DIRECTOR", "rnd_director", "研发总监", null, "RND_DIRECTOR", Instant.now().plusSeconds(60));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, director)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reusedControlPointInAnotherMajorDraft()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].processCode").value("CHILL"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].stepCode").value("COOL"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].resolved").value(false))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").doesNotExist());
    }

    private String criticalDeviationDraft(String instruction) {
        return """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"HEAT","processName":"热加工","yieldBasis":"PRIMARY_INPUT","remark":"损耗已说明","steps":[{
                    "sequence":1,"stepCode":"COOK","stepName":"熟制","stepType":"NORMAL","instruction":"%s",
                    "materials":[{"sequence":1,"materialRole":"PRIMARY","materialName":"牛肉","materialState":"SOLID","weightKg":10,"formulaMaterialId":"BEEF","sourceType":"EXTERNAL"}],
                    "outputs":[{"id":"OUT-DEVIATION","sequence":1,"outputType":"FINISHED","outputName":"熟制牛肉","materialState":"SOLID","weightKg":8,"primaryOutput":true,"continueFlow":false}],
                    "controlPoints":[{"id":"CP-DEVIATION","sequence":1,"controlType":"FOOD_SAFETY","importance":"CRITICAL","itemName":"中心温度","lowerLimit":75,
                      "resolved":true,"confirmedBy":"伪造总监","confirmedAt":"2026-08-19T21:15:00","basisOrRemark":"复测合格","measurements":[{"id":"M-DEVIATION","sequence":1,"measuredValue":72,"result":"FAIL","deviationAction":"继续加热","retestResult":"PASS"}]}]
                  }],"inputs":[],"outputs":[]
                }]}""".formatted(instruction);
    }

    private void saveAndConfirmCriticalDeviation() throws Exception {
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(criticalDeviationDraft("首次熟制")))
                .andExpect(status().isOk());
        var director = new SessionPrincipal("USER-DIRECTOR", "rnd_director", "研发总监", null, "RND_DIRECTOR", Instant.now().plusSeconds(60));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/control-points/{pointId}/confirm-deviation", FORM_ID, "CP-DEVIATION")
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, director)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNote\":\"复测合格\"}"))
                .andExpect(status().isOk());
    }

    private String criticalDeviationDraftWithLocation(String instruction, int versionNo, String majorId, String stepId) {
        var draft = criticalDeviationDraft(instruction).replace("\"versionNo\":0", "\"versionNo\":" + versionNo);
        if (majorId != null) {
            draft = draft.replace("\"sequence\":1,\"processCode\":\"HEAT\"",
                    "\"id\":\"" + majorId + "\",\"sequence\":1,\"processCode\":\"HEAT\"");
        }
        if (stepId != null) {
            draft = draft.replace("\"sequence\":1,\"stepCode\":\"COOK\"",
                    "\"id\":\"" + stepId + "\",\"sequence\":1,\"stepCode\":\"COOK\"");
        }
        return draft;
    }

    private String currentMajorId() {
        return jdbc.queryForObject("select major.id from experiment_major_process major join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?", String.class, FORM_ID);
    }

    private String currentStepId() {
        return jdbc.queryForObject("select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?", String.class, FORM_ID);
    }

    private String reusedControlPointInAnotherMajorDraft() {
        return """
                {"versionNo":1,"status":"DRAFT","majorProcesses":[{
                  "sequence":1,"processCode":"CHILL","processName":"冷却","yieldBasis":"PRIMARY_INPUT","remark":"转入冷却","steps":[{
                    "sequence":1,"stepCode":"COOL","stepName":"冷却定型","stepType":"NORMAL","instruction":"另一工序",
                    "materials":[{"sequence":1,"materialRole":"PRIMARY","materialName":"熟制牛肉","materialState":"SEMI_SOLID","weightKg":8,"formulaMaterialId":"BEEF","sourceType":"EXTERNAL"}],
                    "outputs":[{"id":"OUT-CHILL","sequence":1,"outputType":"FINISHED","outputName":"冷却牛肉","materialState":"SEMI_SOLID","weightKg":8,"primaryOutput":true,"continueFlow":false}],
                    "controlPoints":[{"id":"CP-DEVIATION","sequence":1,"controlType":"FOOD_SAFETY","importance":"CRITICAL","itemName":"中心温度","lowerLimit":75,
                      "resolved":true,"confirmedBy":"伪造总监","confirmedAt":"2026-08-19T21:15:00","basisOrRemark":"复测合格","measurements":[{"id":"M-DEVIATION","sequence":1,"measuredValue":72,"result":"FAIL","deviationAction":"继续加热","retestResult":"PASS"}]}]
                  }],"inputs":[],"outputs":[]
                }]}""";
    }

    @Test
    void submitsWithTheTrustedSessionNameAndExposesRevisionHistoryEndpoints() throws Exception {
        saveReadyDraft();
        var principal = new SessionPrincipal("USER-PROCESS", "rnd_engineer", "会话研发", "ou-process", "RND_ENGINEER", Instant.now().plusSeconds(60));

        var body = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":1,\"confirmed\":true,\"changeReason\":\"首次正式提交\",\"submittedBy\":\"伪造用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(1))
                .andExpect(jsonPath("$.data.submittedBy").value("会话研发"))
                .andReturn().getResponse().getContentAsString();
        var revisionId = body.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];

        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(revisionId));
        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.balanceToleranceKg").value(0.01));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/new-draft", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"changeReason\":\"调整熟制时间\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sourceRevisionId").value(revisionId));
    }

    @Test
    void requiresTrustedSessionForFormalSubmission() throws Exception {
        saveReadyDraft();

        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":1,\"confirmed\":true,\"submittedBy\":\"伪造用户\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_PRINCIPAL_REQUIRED"));
    }

    @Test
    void rejectsProcessDraftSaveByAnotherEngineer() throws Exception {
        var draft = """
                {"versionNo":0,"status":"DRAFT","majorProcesses":[{"sequence":1,"processName":"热加工","yieldBasis":"PRIMARY_INPUT","steps":[],"inputs":[],"outputs":[]}]}
                """;
        var intruder = new SessionPrincipal("USER-INTRUDER", "other", "其他研发", null, "RND_ENGINEER", Instant.now().plusSeconds(60));
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, intruder)
                        .contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_PLAN_FORM_FORBIDDEN"));
    }

    @Test
    void generatesListsAndDownloadsFormalRevisionArtifactsWithTheSessionPrincipal() throws Exception {
        saveReadyDraft();
        var principal = new SessionPrincipal("USER-PROCESS", "rnd_engineer", "会话研发", "ou-process", "RND_ENGINEER", Instant.now().plusSeconds(60));
        var revisionBody = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":1,\"confirmed\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var revisionId = revisionBody.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];

        var artifactBody = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"artifactType\":\"FORMULA_XLSX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedBy").value("会话研发"))
                .andReturn().getResponse().getContentAsString();
        var artifactId = artifactBody.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];

        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(artifactId));
        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts/{artifactId}/download", FORM_ID, revisionId, artifactId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void testerArtifactListContainsOnlyReadyPublicMetadata() throws Exception {
        saveReadyDraft();
        var owner = ownerPrincipal();
        var revisionBody = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":1,\"confirmed\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var revisionId = revisionBody.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];
        var readyBody = mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"artifactType\":\"FORMULA_XLSX\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var readyId = readyBody.split("\\\"id\\\":\\\"")[1].split("\\\"")[0];
        jdbc.update("insert into experiment_process_artifact(id,process_revision_id,artifact_type,document_version,status,generated_at,generated_by,generated_by_user_id,storage_key,content_sha256,byte_size,content_summary,failure_reason) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "PART-FAILED-PUBLIC", revisionId, "SOP_DOCX", "1", "FAILED", LocalDateTime.now(), "内部生成人", "SECRET-USER", "secret/storage/key", "secret-hash", 99L, "internal summary", "SECRET FAILURE");
        jdbc.update("insert into user_account(id,username,password_hash,name,role,status,created_at,updated_at) values (?,?,?,?,?,?,?,?)",
                "USER-TESTER-PUBLIC", "tester_public", "x", "测试只读", "TESTER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now());
        jdbc.update("insert into test_assignment(id,experiment_form_id,task_id,version_id,process_revision_id,tester_name,tester_user_id,status,assigned_at) values (?,?,?,?,?,?,?,?,?)",
                "TEST-PROCESS-PUBLIC", FORM_ID, "TASK-PROCESS-CONTROLLER", "VER-PROCESS-CONTROLLER", revisionId,
                "测试只读", "USER-TESTER-PUBLIC", "PENDING_TEST", LocalDateTime.now());
        var tester = new SessionPrincipal("USER-TESTER-PUBLIC", "tester_public", "测试只读", null, "TESTER", Instant.now().plusSeconds(60));

        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", FORM_ID, revisionId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, tester))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(readyId))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].contentSha256").doesNotExist())
                .andExpect(jsonPath("$.data[0].generatedByUserId").doesNotExist())
                .andExpect(jsonPath("$.data[0].failureReason").doesNotExist())
                .andExpect(jsonPath("$..failureReason", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SECRET FAILURE"))));
    }

    @Test
    void confirmsPassingCriticalPointWithSessionIdentityAndPreservesItOnSave() throws Exception {
        var draft = criticalDeviationDraft("正常实测")
                .replace("\"measuredValue\":72", "\"measuredValue\":80")
                .replace("\"result\":\"FAIL\"", "\"result\":\"PASS\"")
                .replace("伪造总监", "__SESSION_CONFIRMATION_REQUESTED__");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.readTree(draft);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/majorProcesses/0/steps/0/controlPoints/0"))
                .put("confirmedBy", "__SESSION_CONFIRMATION_REQUESTED__");
        var saved = mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                .contentType(MediaType.APPLICATION_JSON).content(tree.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").value("会话研发"))
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.readTree(saved).get("data").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majorProcesses[0].steps[0].controlPoints[0].confirmedBy").value("会话研发"));
    }

    @Test
    void comparisonUsesFormalSnapshotsAndReturnsMeasuredWeights() throws Exception {
        saveReadyDraft();
        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/comparison", FORM_ID)
                .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", FORM_ID)
                .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":1,\"confirmed\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/comparison", FORM_ID)
                .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].processCode").value("COOK"))
                .andExpect(jsonPath("$.data[0].inputKg").value(10))
                .andExpect(jsonPath("$.data[0].outputKg").value(10))
                .andExpect(jsonPath("$.data[0].yieldPercent").value(100));
    }

    private void saveReadyDraft() throws Exception {
        var readyDraft = """
                {"versionNo":0,"status":"DRAFT","balanceToleranceKg":0.01,"majorProcesses":[{
                  "sequence":1,"processCode":"COOK","processName":"熟制","yieldBasis":"PRIMARY_INPUT","steps":[{
                    "sequence":1,"stepCode":"COOK","stepName":"熟制","stepType":"NORMAL",
                    "materials":[{"sequence":1,"materialRole":"PRIMARY","materialCode":"BEEF","materialName":"鲜牛腩","materialState":"SOLID","weightKg":10,"formulaMaterialId":"MAT-BEEF","sourceType":"EXTERNAL"}],
                    "outputs":[{"id":"OUT-CONTROLLER","sequence":1,"outputType":"FINISHED","outputName":"熟制牛腩","materialState":"SEMI_SOLID","weightKg":10,"primaryOutput":true,"continueFlow":false}],"controlPoints":[]
                  }],"inputs":[],"outputs":[]
                }]}""";
        mockMvc.perform(put("/api/v1/experiment-forms/{formId}/process-plan", FORM_ID)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(readyDraft))
                .andExpect(status().isOk());
    }

    private SessionPrincipal ownerPrincipal() {
        return new SessionPrincipal("USER-PROCESS", "rnd_engineer", "会话研发", "ou-process", "RND_ENGINEER", Instant.now().plusSeconds(60));
    }
}
