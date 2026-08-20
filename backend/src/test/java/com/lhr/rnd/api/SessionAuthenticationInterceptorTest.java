package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.ProcessPlanService;
import com.lhr.rnd.service.ProcessRevisionService;
import com.lhr.rnd.service.SessionProperties;
import com.lhr.rnd.service.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rnd.session.auth-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAuthenticationInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionAuthenticationInterceptor sessionAuthenticationInterceptor;

    @Autowired
    private SessionProperties sessionProperties;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessPlanService processPlanService;

    @Autowired
    private ProcessRevisionService processRevisionService;

    @Test
    void protectsBusinessApisAndAllowsAccessWithSessionToken() throws Exception {
        mockMvc.perform(get("/api/v1/sample-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_TOKEN_REQUIRED"));

        bindUser();
        var token = oauthToken();

        mockMvc.perform(get("/api/v1/sample-requests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void letsAnAuthenticatedEngineerReachSubmissionCheckWithoutTheTestBypass() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var engineerToken = tokenFor("工艺检查研发", "ou_submission_check_engineer", "RND_ENGINEER");

            mockMvc.perform(get("/api/v1/experiment-forms/FORM-AUTH-CHECK/process-plan/submission-check")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EXPERIMENT_FORM_NOT_FOUND"));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void grantsRevisionReadsToExistingProcessReadersButRestrictsRevisionWritesToProcessEditors() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var engineerToken = tokenFor("正式版本研发", "ou_revision_engineer", "RND_ENGINEER");
            var testerToken = tokenFor("正式版本测试", "ou_revision_tester", "TESTER");

            mockMvc.perform(get("/api/v1/experiment-forms/FORM-AUTH-REVISION/process-plan/revisions")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EXPERIMENT_FORM_NOT_FOUND"));
            mockMvc.perform(get("/api/v1/experiment-forms/FORM-AUTH-REVISION/process-plan/revisions/REV-1")
                            .header("Authorization", "Bearer " + testerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EXPERIMENT_FORM_NOT_FOUND"));
            mockMvc.perform(post("/api/v1/experiment-forms/FORM-AUTH-REVISION/process-plan/submit")
                            .header("Authorization", "Bearer " + testerToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"confirmed\":true}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void enforcesRealSessionRolesAndFormOwnershipForFormalArtifactGenerationAndDownload() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var formId = "FORM-AUTH-ARTIFACT";
            seedProcessForm(formId, "制品归属研发");
            var draft = processPlanService.save(formId, readyPlan(formId, processPlanService.find(formId).versionNo()));
            var revision = processRevisionService.submit(formId, new ProcessRevisionService.SubmitCommand(
                    draft.versionNo(), true, "首次正式提交", "制品归属研发"));
            var owner = tokenFor("制品归属研发", "ou_process_artifact_owner", "RND_ENGINEER");
            var tester = tokenFor("制品只读测试", "ou_process_artifact_tester", "TESTER");
            var otherEngineer = tokenFor("制品越权研发", "ou_process_artifact_other", "RND_ENGINEER");

            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", formId, revision.id())
                            .header("Authorization", "Bearer " + owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"artifactType\":\"SOP_DOCX\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.generatedBy").value("制品归属研发"));
            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", formId, revision.id())
                            .header("Authorization", "Bearer " + tester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].artifactType").value("SOP_DOCX"));
            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", formId, revision.id())
                            .header("Authorization", "Bearer " + tester)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"artifactType\":\"FORMULA_XLSX\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/artifacts", formId, revision.id())
                            .header("Authorization", "Bearer " + otherEngineer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"artifactType\":\"FORMULA_XLSX\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROCESS_PLAN_FORM_FORBIDDEN"));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void deniesAnEngineerFormalWritesForAFormAssignedToAnotherEngineerButAllowsDirectorPolicyOverride() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var formId = "FORM-AUTH-OWNERSHIP";
            seedProcessForm(formId, "归属研发A");
            var draft = processPlanService.save(formId, readyPlan(formId, processPlanService.find(formId).versionNo()));
            var otherEngineer = tokenFor("越权研发B", "ou_process_owner_b", "RND_ENGINEER");

            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/submit", formId)
                            .header("Authorization", "Bearer " + otherEngineer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"versionNo\":%d,\"confirmed\":true}".formatted(draft.versionNo())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROCESS_PLAN_FORM_FORBIDDEN"));

            var revision = processRevisionService.submit(formId, new ProcessRevisionService.SubmitCommand(
                    draft.versionNo(), true, "首次正式提交", "归属研发A"));
            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/new-draft", formId, revision.id())
                            .header("Authorization", "Bearer " + otherEngineer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeReason\":\"越权恢复\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROCESS_PLAN_FORM_FORBIDDEN"));

            var director = tokenFor("工艺总监", "ou_process_director", "RND_DIRECTOR");
            mockMvc.perform(post("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/new-draft", formId, revision.id())
                            .header("Authorization", "Bearer " + director)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeReason\":\"总监复核\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void servesLatestFormalSnapshotToTesterInsteadOfReopenedMutableDraft() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var formId = "FORM-AUTH-TESTER-REVISION";
            seedProcessForm(formId, "归属研发测试");
            var draft = processPlanService.save(formId, readyPlan(formId, processPlanService.find(formId).versionNo()));
            var tester = tokenFor("工艺测试员", "ou_process_tester", "TESTER");
            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions", formId)
                            .header("Authorization", "Bearer " + tester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());

            var revision = processRevisionService.submit(formId, new ProcessRevisionService.SubmitCommand(
                    draft.versionNo(), true, "首次正式提交", "归属研发测试"));
            processRevisionService.createDraftFromRevision(formId, revision.id(), "研发修改中");

            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan", formId)
                            .header("Authorization", "Bearer " + tester))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}", formId, revision.id())
                            .header("Authorization", "Bearer " + tester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.snapshot.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.revisionNo").value(1));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void preventsAnUnassignedEngineerFromReadingAnotherEngineersDraft() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(true);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var formId = "FORM-AUTH-DRAFT-READ";
            seedProcessForm(formId, "归属研发读");
            processPlanService.save(formId, readyPlan(formId, processPlanService.find(formId).versionNo()));
            var intruder = tokenFor("越权研发读", "ou_process_read_intruder", "RND_ENGINEER");
            var director = tokenFor("工艺负责人读", "ou_process_read_director", "RND_DIRECTOR");

            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan", formId)
                            .header("Authorization", "Bearer " + intruder))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROCESS_PLAN_FORM_FORBIDDEN"));
            mockMvc.perform(get("/api/v1/experiment-forms/{formId}/process-plan", formId)
                            .header("Authorization", "Bearer " + director))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void allowsFeishuIntegrationStatusWithoutSessionToken() throws Exception {
        mockMvc.perform(get("/api/v1/feishu/integration/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").exists());
    }

    @Test
    void attachesBearerPrincipalWhenAuthorizationIsOptional() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(false);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            var token = sessionTokenService.issue(new UserAccount(
                    "USER-H5-REVIEW",
                    "rnd_engineer",
                    "张研发",
                    null,
                    "RND_ENGINEER",
                    "研发部",
                    "ACTIVE",
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            ));
            var request = new MockHttpServletRequest("POST", "/api/v1/pricing-files/PRICE-001/review");
            request.addHeader("Authorization", "Bearer " + token);

            assertThat(sessionAuthenticationInterceptor.preHandle(
                    request,
                    new MockHttpServletResponse(),
                    new Object()
            )).isTrue();
            assertThat(request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE))
                    .extracting("name")
                    .isEqualTo("张研发");
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void optionalAuthenticationStillRequiresSessionAndEnforcesRolePermissionsForBusinessApis() throws Exception {
        var authRequired = sessionProperties.isAuthRequired();
        var testBypass = sessionProperties.isTestBusinessApiAuthenticationBypass();
        sessionProperties.setAuthRequired(false);
        sessionProperties.setTestBusinessApiAuthenticationBypass(false);
        try {
            mockMvc.perform(get("/api/v1/sample-requests"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SESSION_TOKEN_REQUIRED"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"rnd_assistant\",\"password\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

            var engineerToken = tokenFor("可选认证研发", "ou_optional_engineer", "RND_ENGINEER");
            mockMvc.perform(post("/api/v1/sample-requests")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleRequestJson()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

            var assistantToken = tokenFor("可选认证内勤", "ou_optional_assistant", "RND_ASSISTANT");
            mockMvc.perform(post("/api/v1/sample-requests")
                            .header("Authorization", "Bearer " + assistantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleRequestJson()))
                    .andExpect(status().isOk());
        } finally {
            sessionProperties.setAuthRequired(authRequired);
            sessionProperties.setTestBusinessApiAuthenticationBypass(testBypass);
        }
    }

    @Test
    void enforcesRolePermissionsForSampleRequestCreationAndReview() throws Exception {
        var assistantToken = tokenFor("研发内勤A", "ou_assistant_001", "RND_ASSISTANT");
        var directorToken = tokenFor("研发总监A", "ou_director_001", "RND_DIRECTOR");
        var engineerToken = tokenFor("研发人员A", "ou_engineer_001", "RND_ENGINEER");

        mockMvc.perform(post("/api/v1/sample-requests")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

        var requestId = mockMvc.perform(post("/api/v1/sample-requests")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PENDING_ASSIGNMENT"));
    }

    @Test
    void enforcesRolePermissionsForTaskAssignmentAndAcceptance() throws Exception {
        var assistantToken = tokenFor("研发内勤B", "ou_assistant_002", "RND_ASSISTANT");
        var directorToken = tokenFor("研发总监B", "ou_director_002", "RND_DIRECTOR");
        var engineerToken = tokenFor("研发人员B", "ou_engineer_002", "RND_ENGINEER");

        var requestId = mockMvc.perform(post("/api/v1/sample-requests")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        var taskId = mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监B\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"task\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"研发人员B\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"研发人员B\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"研发人员B\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"研发人员B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAMPLING"));
    }

    @Test
    void restrictsAccountManagementToSuperAdminsWhileAllowingDirectorAssigneeLookup() throws Exception {
        var directorToken = tokenFor("待办总监", "ou_todo_director", "RND_DIRECTOR");
        var superAdminToken = tokenFor("超级管理员", "ou_todo_super_admin", "SYSTEM_ADMIN");

        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/rnd-assignees")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
    }

    private String tokenFor(String name, String feishuUserId, String role) throws Exception {
        bindUser(name, feishuUserId, role);
        return oauthToken(feishuUserId);
    }

    private void seedProcessForm(String formId, String assigneeName) {
        jdbc.update("delete from experiment_process_revision where experiment_form_id = ?", formId);
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id join experiment_process_plan plan on major.process_plan_id = plan.id where plan.experiment_form_id = ?)", formId);
        jdbc.update("delete from experiment_major_process where process_plan_id in (select id from experiment_process_plan where experiment_form_id = ?)", formId);
        jdbc.update("delete from experiment_process_plan where experiment_form_id = ?", formId);
        jdbc.update("delete from experiment_form where id = ?", formId);
        var suffix = formId.substring("FORM-AUTH-".length());
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "REQ-" + suffix, "S-" + suffix, "牛腩", "预制菜", "客户", "1kg", "研发", "APPROVED", now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",
                "PRJ-" + suffix, "REQ-" + suffix, "S-" + suffix, "牛腩", "预制菜", "客户", "1kg", "ACTIVE", now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                "VER-" + suffix, "PRJ-" + suffix, "S-" + suffix, "牛腩", "预制菜", "1kg", "1", 1, "V1", now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,assignee_name,created_at) values (?,?,?,?,?,?,?,?,?)",
                "TASK-" + suffix, "PRJ-" + suffix, "VER-" + suffix, "S-" + suffix, "牛腩", "V1", "IN_PROGRESS", assigneeName, now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",
                formId, "TASK-" + suffix, "PRJ-" + suffix, "VER-" + suffix, "S-" + suffix, "牛腩", "V1", "DRAFT", assigneeName, now);
    }

    private ProcessPlan readyPlan(String formId, int versionNo) {
        var step = new ProcessPlan.MinorStep(null, 1, "COOK", "熟制", "NORMAL", null, null, null, null, null, null,
                null, null, List.of(new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "BEEF", "鲜牛腩", "SOLID",
                new BigDecimal("10"), "MAT-BEEF", null, "EXTERNAL", null)), List.of(
                new ProcessPlan.StepOutput(null, 1, "FINISHED", "熟制牛腩", "SEMI_SOLID", new BigDecimal("10"), true, false, null)), List.of());
        var major = new ProcessPlan.MajorProcess(null, 1, "COOK", "熟制", null, "PRIMARY_INPUT", null,
                List.of(step), List.of(), List.of(), null);
        return new ProcessPlan(null, formId, versionNo, "DRAFT", List.of(major), null, new BigDecimal("0.01"), false);
    }

    private void bindUser() throws Exception {
        bindUser("鉴权研发", "ou_auth_rnd_001", "RND_ENGINEER");
    }

    private void bindUser(String name, String feishuUserId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/feishu/users/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "feishuUserId": "%s",
                                  "role": "%s",
                                  "departmentName": "研发部"
                                }
                                """.formatted(name, feishuUserId, role)))
                .andExpect(status().isOk());
    }

    private String oauthToken() throws Exception {
        return oauthToken("ou_auth_rnd_001");
    }

    private String oauthToken(String feishuUserId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/feishu/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock:%s\"}".formatted(feishuUserId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String sampleRequestJson() {
        return """
                {
                  "productName": "500g香卤大肠头",
                  "productType": "冷冻即热菜",
                  "customerName": "LHYC",
                  "specification": "500g/袋",
                  "applicationScenario": "商超零售冷冻即热，家庭复热即食",
                  "flavorRequirement": "香卤风味，微辣，复热后卤香明显",
                  "creatorName": "研发内勤"
                }
                """;
    }
}
