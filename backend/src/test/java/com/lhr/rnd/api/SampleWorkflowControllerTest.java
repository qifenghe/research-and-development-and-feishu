package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.PricingFileRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SampleWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private com.lhr.rnd.service.SampleWorkflowService workflowService;

    @Autowired
    private com.lhr.rnd.service.ProcessPlanService processPlanService;

    @Autowired
    private com.lhr.rnd.service.ProcessRevisionService processRevisionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private com.lhr.rnd.service.LocalArchiveStorageService archiveStorageService;

    @SpyBean
    private com.lhr.rnd.service.PricingArchiveCleanupLedgerService pricingArchiveCleanupLedger;

    @BeforeEach
    void clearWorkflowState() {
        clearBusinessTables();
        jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values ('TEST-ASSIGNEE','test_assignee','x','张研发','ou-test-assignee','RND_ENGINEER','ACTIVE',current_timestamp,current_timestamp)");
        jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values ('TEST-TESTER','test_tester','x','内部测试员','ou-test-tester','TESTER','ACTIVE',current_timestamp,current_timestamp)");
        clearWorkflowServiceMemory();
    }

    private void clearBusinessTables() {
        for (String table : new String[]{
                "pricing_archive_cleanup_ledger",
                "process_artifact_cleanup_ledger",
                "audit_log",
                "archive_file",
                "finance_notification",
                "pricing_packaging_item",
                "pricing_file",
                "customer_feedback",
                "shipment_record",
                "test_record",
                "test_assignment",
                "experiment_process_artifact",
                "experiment_process_revision",
                "experiment_control_measurement",
                "experiment_control_point",
                "experiment_step_output",
                "experiment_step_material",
                "experiment_minor_step",
                "experiment_major_process",
                "experiment_process_plan",
                "experiment_process",
                "experiment_material",
                "experiment_form",
                "feishu_notification",
                "rnd_task",
                "user_account",
                "sample_version",
                "sample_project",
                "sample_request",
                "workflow_rule_config"
        }) {
            jdbcTemplate.update("delete from " + table);
        }
    }

    @SuppressWarnings("unchecked")
    private void clearWorkflowServiceMemory() {
        for (String field : new String[]{
                "requests",
                "projects",
                "versions",
                "tasks",
                "experimentForms",
                "testAssignments",
                "testRecords",
                "shipments",
                "customerFeedbacks",
                "pricingFiles",
                "financeNotifications"
        }) {
            ((Map<?, ?>) ReflectionTestUtils.getField(workflowService, field)).clear();
        }
        for (String sequence : new String[]{
                "requestSequence",
                "taskSequence",
                "experimentSequence",
                "testAssignmentSequence",
                "testRecordSequence",
                "shipmentSequence",
                "customerFeedbackSequence",
                "pricingFileSequence",
                "financeNotificationSequence"
        }) {
            ReflectionTestUtils.setField(workflowService, sequence, 1);
        }
    }

    @Test
    void createsRequestApprovesToProjectAndAssignsTask() throws Exception {
        var taskId = createApprovedRequest();
        var sampleNo = valueById("rnd_task", taskId, "sample_no");

        mockMvc.perform(get("/api/v1/rnd-tasks/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sampleNo").value(sampleNo))
                .andExpect(jsonPath("$.data[0].productName").value("500g香卤大肠头"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.assigneeName").value("张研发"));
    }

    @Test
    void approvingRequestUsesConfiguredWorkflowTransitionForProjectStatus() throws Exception {
        jdbcTemplate.update(
                """
                        insert into workflow_rule_config (
                            id, workflow_code, current_status, action_code, action_label, next_status,
                            enabled, notify_feishu, notify_role, sort_order, remark, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "FLOW-APPROVE-001",
                "SAMPLE_RND_FLOW",
                "PENDING_REVIEW",
                "APPROVE_REQUEST",
                "审核通过直接归档",
                "ARCHIVED",
                true,
                false,
                null,
                10,
                "测试业务服务读取配置"
        );

        var requestId = createPendingSampleRequest();

        mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status").value("ARCHIVED"));

        assertThat(countByColumn("sample_project", "status", "ARCHIVED")).isGreaterThan(0);
    }

    @Test
    void taskAssignmentUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        disableWorkflowAction("PENDING_ASSIGNMENT", "ASSIGN_TASK", "PENDING_ACCEPTANCE");

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void acceptingTaskUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        disableWorkflowAction("PENDING_ACCEPTANCE", "ACCEPT_TASK", "SAMPLING");

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"张研发\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void savesStructuredExperimentDraftAndRecalculatesFormulaLossAndYield() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        var response = mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "summary": "结构化实验草稿",
                                  "materials": [
                                    {
                                      "stage": "原料",
                                      "sequence": 1,
                                      "materialCode": "RAW-001",
                                      "materialName": "主原料",
                                      "weightKg": 10,
                                      "materialCategory": "RAW",
                                      "primaryMaterial": true,
                                      "formulaRatio": 99
                                    },
                                    {
                                      "stage": "辅料",
                                      "sequence": 2,
                                      "materialCode": "AUX-001",
                                      "materialName": "辅料",
                                      "weightKg": 2
                                    }
                                  ],
                                  "processSteps": [
                                    {
                                      "sequence": 1,
                                      "processName": "蒸煮",
                                      "beforeWeightKg": 10,
                                      "afterWeightKg": 8,
                                      "remainingWeightKg": 0.5,
                                      "remainingDisposition": "RETURNED",
                                      "lossWeightKg": 99,
                                      "lossRate": 99
                                    }
                                  ],
                                  "finishedOutputWeightKg": 8,
                                  "finishedYieldPercent": 99
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].materialCategory").value("RAW"))
                .andExpect(jsonPath("$.data.materials[0].primaryMaterial").value(true))
                .andExpect(jsonPath("$.data.materials[0].utilizationRate").value(1.0))
                .andExpect(jsonPath("$.data.materials[0].formulaRatio").value(0.833333))
                .andExpect(jsonPath("$.data.materials[0].inputUnit").value("kg"))
                .andExpect(jsonPath("$.data.materials[1].materialCategory").value("AUXILIARY"))
                .andExpect(jsonPath("$.data.materials[1].formulaRatio").value(0.166667))
                .andExpect(jsonPath("$.data.processSteps[0].remainingWeightKg").value(0.5))
                .andExpect(jsonPath("$.data.processSteps[0].remainingDisposition").value("RETURNED"))
                .andExpect(jsonPath("$.data.processSteps[0].lossWeightKg").value(1.5))
                .andExpect(jsonPath("$.data.processSteps[0].lossRate").value(0.15))
                .andExpect(jsonPath("$.data.finishedOutputWeightKg").value(8.0))
                .andExpect(jsonPath("$.data.finishedYieldPercent").value(80.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var formId = objectMapper.readTree(response).path("data").path("id").asText();
        assertThat(jdbcTemplate.queryForObject(
                "select finished_yield_percent from experiment_form where id = ?",
                java.math.BigDecimal.class,
                formId
        )).isEqualByComparingTo("80");
        assertThat(jdbcTemplate.queryForObject(
                "select formula_ratio from experiment_material where experiment_form_id = ? and sequence = 2",
                java.math.BigDecimal.class,
                formId
        )).isEqualByComparingTo("0.166667");
        assertThat(jdbcTemplate.queryForObject(
                "select sum(formula_ratio) from experiment_material where experiment_form_id = ?",
                java.math.BigDecimal.class,
                formId
        )).isEqualByComparingTo("1.000000");
        assertThat(jdbcTemplate.queryForObject(
                "select loss_weight_kg from experiment_process where experiment_form_id = ? and sequence = 1",
                java.math.BigDecimal.class,
                formId
        )).isEqualByComparingTo("1.5");
    }

    @Test
    void experimentDraftWriteUsesSessionOwnerIdentityAndRejectsAnotherEngineer() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var payload = """
                {"operatorName":"伪造研发","materials":[{"stage":"原料","sequence":1,"materialName":"主料","weightKg":10,"materialCategory":"RAW","primaryMaterial":true}],
                 "processSteps":[{"sequence":1,"processName":"熟制","beforeWeightKg":10,"afterWeightKg":8}],
                 "finishedOutputWeightKg":8,"finishedOutputQuantity":8,"finishedOutputUnit":"袋"}
                """;
        var intruder = new SessionPrincipal("OTHER-ENGINEER", "other", "张研发", null, "RND_ENGINEER", Instant.now().plusSeconds(60));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, intruder)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_FORM_TASK_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE,
                                ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorName").value("张研发"));
    }

    @Test
    void testHandoffRequiresAndBindsCurrentFormalProcessRevision() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var formId = saveExperimentDraftWithPrincipal(taskId, ownerPrincipal());

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", formId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"testerName\":\"AUTO_ASSIGN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_FORMAL_REVISION_REQUIRED"));

        var revisionId = submitFormalProcessRevision(formId, ownerPrincipal());
        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", formId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"testerName\":\"张研发\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TESTER_ACCOUNT_NOT_UNIQUE"));

        var response = mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", formId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"testerName\":\"AUTO_ASSIGN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testAssignment.testerName").value("内部测试员"))
                .andExpect(jsonPath("$.data.testAssignment.testerUserId").value("TEST-TESTER"))
                .andReturn().getResponse().getContentAsString();
        var assignmentId = response.split("\\\"testAssignment\\\":\\{\\\"id\\\":\\\"")[1].split("\"")[0];

        assertThat(valueById("test_assignment", assignmentId, "process_revision_id")).isEqualTo(revisionId);
        assertThat(processRevisionService.list(formId, testerPrincipal())).extracting(com.lhr.rnd.model.ProcessRevision.ProcessRevisionSummary::id)
                .containsExactly(revisionId);
        assertThatThrownBy(() -> processRevisionService.find(formId, revisionId,
                new SessionPrincipal("OTHER-TESTER", "other_tester", "内部测试员", null, "TESTER", Instant.now().plusSeconds(60))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_REVISION_NOT_ASSIGNED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where business_type='EXPERIMENT_FORM' and business_id=? and action='SUBMIT_FOR_TEST' and operator_user_id='TEST-ASSIGNEE'", Integer.class, formId)).isEqualTo(1);
        assertThatThrownBy(() -> processRevisionService.createDraftFromRevision(formId, revisionId, "送测后漂移版本", ownerPrincipal()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PROCESS_EDIT_AFTER_TEST_FORBIDDEN");
    }

    @Test
    void savesFinishedQuantityAndProductOwner() throws Exception {
        var taskId = createApprovedRequest();
        jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values ('TEST-LI','test_li','x','李研发','ou-test-li','RND_ENGINEER','ACTIVE',current_timestamp,current_timestamp)");

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeName": "李研发",
                                  "productOwnerName": "张研发",
                                  "dueDate": "2026-06-25"
                                }
                                """))
                .andExpect(status().isOk());
        acceptTask(taskId, "李研发");

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE,
                                new SessionPrincipal("TEST-LI", "test_li", "李研发", "ou-test-li", "RND_ENGINEER", Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "李研发",
                                  "finishedOutputWeightKg": 8.5,
                                  "finishedOutputQuantity": 17,
                                  "finishedOutputUnit": "袋"
                                }
                                """))
                .andExpect(status().isOk());

        ((Map<?, ?>) ReflectionTestUtils.getField(workflowService, "tasks")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(workflowService, "experimentForms")).clear();

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER")
                        .param("operatorName", "李研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.productOwnerName").value("张研发"))
                .andExpect(jsonPath("$.data.currentExperimentForm.finishedOutputQuantity").value(17))
                .andExpect(jsonPath("$.data.currentExperimentForm.finishedOutputUnit").value("袋"));
    }

    @Test
    void savesAPositiveIntegerFinishedOutputQuantity() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "finishedOutputQuantity": 17,
                                  "finishedOutputUnit": "袋"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finishedOutputQuantity").value(17));
    }

    @Test
    void rejectsFractionalFinishedOutputQuantityAtRequestBoundary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "finishedOutputQuantity": 17.5,
                                  "finishedOutputUnit": "袋"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINISHED_OUTPUT_QUANTITY_INVALID"));
    }

    @Test
    void rejectsZeroFinishedOutputQuantityAtRequestBoundary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "finishedOutputQuantity": 0,
                                  "finishedOutputUnit": "袋"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeFinishedOutputQuantityAtRequestBoundary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "finishedOutputQuantity": -1,
                                  "finishedOutputUnit": "袋"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void normalizesOmittedAndBlankFinishedOutputUnitsToBag() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        for (String payload : new String[]{
                "{\"operatorName\":\"张研发\"}",
                "{\"operatorName\":\"张研发\",\"finishedOutputUnit\":\"\"}",
                "{\"operatorName\":\"张研发\",\"finishedOutputUnit\":\"  \"}"
        }) {
            mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                            .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.finishedOutputUnit").value("袋"));
        }
    }

    @Test
    void rejectsNonPositiveFinishedOutputQuantityAtServiceBoundary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        assertThatThrownBy(() -> workflowService.saveExperimentDraft(
                new com.lhr.rnd.service.SampleWorkflowService.SaveExperimentDraftCommand(
                        taskId, "张研发", null, null, null, null, java.math.BigDecimal.ZERO, "袋", null
                )
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("FINISHED_OUTPUT_QUANTITY_INVALID");
    }

    @Test
    void rejectsUnsupportedFinishedOutputUnitAtServiceBoundary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        assertThatThrownBy(() -> workflowService.saveExperimentDraft(
                new com.lhr.rnd.service.SampleWorkflowService.SaveExperimentDraftCommand(
                        taskId, "张研发", null, null, null, null, null, "桶", null
                )
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("FINISHED_OUTPUT_UNIT_INVALID");
    }

    @Test
    void readsCompleteExperimentFormFromPersistenceAfterMemoryCacheIsCleared() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "summary": "持久化回读",
                                  "materials": [
                                    {"stage":"辅料","sequence":2,"materialCode":"AUX-001","materialName":"辅料","weightKg":2},
                                    {"stage":"原料","sequence":1,"materialCode":"RAW-001","materialName":"主原料","weightKg":10,"materialCategory":"RAW","primaryMaterial":true}
                                  ],
                                  "processSteps": [
                                    {"sequence":2,"processName":"冷却","beforeWeightKg":8,"afterWeightKg":7.5,"remainingWeightKg":0.2,"remainingDisposition":"DISCARDED"},
                                    {"sequence":1,"processName":"蒸煮","beforeWeightKg":10,"afterWeightKg":8,"remainingWeightKg":0.5,"remainingDisposition":"RETURNED"}
                                  ],
                                  "finishedOutputWeightKg":8,
                                  "finishedYieldPercent":99
                                }
                                """))
                .andExpect(status().isOk());

        ((Map<?, ?>) ReflectionTestUtils.getField(workflowService, "experimentForms")).clear();

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER")
                        .param("operatorName", "张研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentExperimentForm.taskId").value(taskId))
                .andExpect(jsonPath("$.data.currentExperimentForm.operatorName").value("张研发"))
                .andExpect(jsonPath("$.data.currentExperimentForm.summary").value("持久化回读"))
                .andExpect(jsonPath("$.data.currentExperimentForm.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentExperimentForm.finishedOutputWeightKg").value(8.0))
                .andExpect(jsonPath("$.data.currentExperimentForm.finishedYieldPercent").value(80.0))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[0].sequence").value(1))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[0].materialCategory").value("RAW"))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[0].primaryMaterial").value(true))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[0].formulaRatio").value(0.833333))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[0].inputUnit").value("kg"))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[1].sequence").value(2))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[1].materialCategory").value("AUXILIARY"))
                .andExpect(jsonPath("$.data.currentExperimentForm.materials[1].utilizationRate").value(1.0))
                .andExpect(jsonPath("$.data.currentExperimentForm.processSteps[0].sequence").value(1))
                .andExpect(jsonPath("$.data.currentExperimentForm.processSteps[0].remainingWeightKg").value(0.5))
                .andExpect(jsonPath("$.data.currentExperimentForm.processSteps[0].remainingDisposition").value("RETURNED"))
                .andExpect(jsonPath("$.data.currentExperimentForm.processSteps[0].lossWeightKg").value(1.5))
                .andExpect(jsonPath("$.data.currentExperimentForm.processSteps[1].sequence").value(2));
    }

    @Test
    void savesExperimentDraftWithMultiplePrimaryMaterials() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "yieldCalculationMode": "SELECTED_PRIMARY_MATERIALS",
                                  "finishedOutputWeightKg": 12,
                                  "materials": [
                                    {"stage":"原料","sequence":1,"materialName":"原料A","weightKg":10,"materialCategory":"RAW","primaryMaterial":true},
                                    {"stage":"原料","sequence":2,"materialName":"原料B","weightKg":5,"materialCategory":"RAW","primaryMaterial":true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yieldCalculationMode").value("SELECTED_PRIMARY_MATERIALS"))
                .andExpect(jsonPath("$.data.finishedYieldPercent").value(80.0));
    }

    @Test
    void savesSauceDraftWithoutPrimaryMaterialUsingTotalPickingWeight() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "yieldCalculationMode": "TOTAL_PICKING_WEIGHT",
                                  "finishedOutputWeightKg": 96,
                                  "materials": [
                                    {"stage":"原料","sequence":1,"materialName":"水","weightKg":100,"utilizationRate":1,"materialCategory":"RAW","primaryMaterial":false},
                                    {"stage":"辅料","sequence":2,"materialName":"香辛料","weightKg":10,"utilizationRate":0.5,"materialCategory":"AUXILIARY","primaryMaterial":false},
                                    {"stage":"包材","sequence":3,"materialName":"包装袋","weightKg":3,"utilizationRate":1,"materialCategory":"PACKAGING","primaryMaterial":false}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yieldCalculationMode").value("TOTAL_PICKING_WEIGHT"))
                .andExpect(jsonPath("$.data.finishedYieldPercent").value(80.0));
    }

    @Test
    void rejectsExperimentDraftWithPackagingPrimaryMaterial() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "materials": [
                                    {"stage":"包材","sequence":1,"materialName":"包装袋","weightKg":1,"materialCategory":"PACKAGING","primaryMaterial":true}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_MATERIAL_INVALID"));
    }

    @Test
    void submittingExperimentUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        submitFormalProcessRevision(experimentFormId, ownerPrincipal());
        disableWorkflowAction("SAMPLING", "SUBMIT_EXPERIMENT", "PENDING_TEST");

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void passingInternalTestUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);
        disableWorkflowAction("PENDING_TEST", "TEST_PASS", "SAMPLE_COMPLETED");

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"口味和复热状态通过\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void failingInternalTestUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);
        disableWorkflowAction("PENDING_TEST", "TEST_FAIL_RESAMPLE", "RESAMPLING_REQUIRED");

        mockMvc.perform(post("/api/v1/test-assignments/{id}/fail-resample", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"需要调整咸度后复打样\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void bindingFeishuUserCreatesTaskAssignmentNotificationWhenTaskIsAssigned() throws Exception {
        jdbcTemplate.update("delete from user_account where id = 'TEST-ASSIGNEE'");
        mockMvc.perform(post("/api/v1/feishu/users/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "张研发",
                                  "feishuUserId": "ou_rnd_001",
                                  "role": "RND_ENGINEER",
                                  "departmentName": "研发部"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("张研发"))
                .andExpect(jsonPath("$.data.feishuUserId").value("ou_rnd_001"))
                .andExpect(jsonPath("$.data.role").value("RND_ENGINEER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(valueByColumn("user_account", "feishu_user_id", "ou_rnd_001", "name")).isEqualTo("张研发");
        assertThat(valueByColumn("user_account", "feishu_user_id", "ou_rnd_001", "department_name")).isEqualTo("研发部");

        var taskId = createApprovedRequest();
        assignTask(taskId);

        assertThat(countByColumn("feishu_notification", "business_id", taskId)).isEqualTo(1);
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "business_type")).isEqualTo("RND_TASK");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "recipient_feishu_user_id")).isEqualTo("ou_rnd_001");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "template_key")).isEqualTo("RND_TASK_ASSIGNED");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "status")).isEqualTo("PENDING_SEND");
        var notificationContent = valueByColumn("feishu_notification", "business_id", taskId, "content");
        assertThat(notificationContent).contains("**产品**：500g香卤大肠头");
        assertThat(notificationContent).contains("**版本**：A0");
        assertThat(notificationContent).contains("**任务编号**：" + taskId);
        assertThat(notificationContent).contains("**负责人**：张研发");
        assertThat(notificationContent).contains("**截止日期**：2026-06-25");
        assertThat(notificationContent).contains("请在飞书自建应用中接受任务");

        mockMvc.perform(get("/api/v1/feishu/notifications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].businessId").value(taskId))
                .andExpect(jsonPath("$.data[0].recipientFeishuUserId").value("ou_rnd_001"))
                .andExpect(jsonPath("$.data[0].templateKey").value("RND_TASK_ASSIGNED"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_SEND"));
    }

    @Test
    void dispatchingPendingFeishuNotificationMarksItSentAndRemovesItFromPendingList() throws Exception {
        bindFeishuUser("发送成功研发", "ou_rnd_dispatch_001");
        var taskId = createApprovedRequest();
        assignTask(taskId, "发送成功研发");

        mockMvc.perform(post("/api/v1/feishu/notifications/dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptedCount").value(1))
                .andExpect(jsonPath("$.data.sentCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "status")).isEqualTo("SENT");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "send_attempts")).isEqualTo("1");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "last_error")).isNull();
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "sent_at")).isNotBlank();

        mockMvc.perform(get("/api/v1/feishu/notifications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void dispatchingPendingFeishuNotificationMarksFailedWhenSenderFails() throws Exception {
        bindFeishuUser("发送失败研发", "fail_rnd_dispatch_001");
        var taskId = createApprovedRequest();
        assignTask(taskId, "发送失败研发");

        mockMvc.perform(post("/api/v1/feishu/notifications/dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptedCount").value(1))
                .andExpect(jsonPath("$.data.sentCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1));

        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "status")).isEqualTo("FAILED");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "send_attempts")).isEqualTo("1");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "last_error")).isEqualTo("模拟飞书发送失败");
        assertThat(valueByColumn("feishu_notification", "business_id", taskId, "sent_at")).isNull();

        mockMvc.perform(get("/api/v1/feishu/notifications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void feishuCardActionAcceptsAssignedRndTask() throws Exception {
        bindFeishuUser("卡片研发", "ou_card_rnd_001");
        var taskId = createApprovedRequest();
        assignTask(taskId, "卡片研发");
        mockMvc.perform(post("/api/v1/feishu/notifications/dispatch"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/feishu/card-actions")
                        .header("X-Feishu-Card-Secret", "test-card-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ACCEPT_RND_TASK",
                                  "businessType": "RND_TASK",
                                  "businessId": "%s",
                                  "feishuUserId": "ou_card_rnd_001"
                                }
                                """.formatted(taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("ACCEPT_RND_TASK"))
                .andExpect(jsonPath("$.data.message").value("任务已接受"))
                .andExpect(jsonPath("$.data.task.id").value(taskId))
                .andExpect(jsonPath("$.data.task.status").value("SAMPLING"))
                .andExpect(jsonPath("$.data.task.assigneeName").value("卡片研发"));

        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("SAMPLING");
        assertThat(valueById("rnd_task", taskId, "accepted_at")).isNotBlank();
    }

    @Test
    void feishuCardActionRejectsMissingSecret() throws Exception {
        bindFeishuUser("卡片安全研发", "ou_card_secure_rnd_001");
        var taskId = createApprovedRequest();
        assignTask(taskId, "卡片安全研发");
        mockMvc.perform(post("/api/v1/feishu/notifications/dispatch"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/feishu/card-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ACCEPT_RND_TASK",
                                  "businessType": "RND_TASK",
                                  "businessId": "%s",
                                  "feishuUserId": "ou_card_secure_rnd_001"
                                }
                                """.formatted(taskId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEISHU_CARD_ACTION_UNAUTHORIZED"));

        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_ACCEPTANCE");
        assertThat(valueById("rnd_task", taskId, "accepted_at")).isNull();
    }

    @Test
    void feishuOauthCallbackReturnsBoundSystemUser() throws Exception {
        bindFeishuUser("免登研发", "ou_oauth_001");

        var response = mockMvc.perform(post("/api/v1/feishu/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "mock:ou_oauth_001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feishuUserId").value("ou_oauth_001"))
                .andExpect(jsonPath("$.data.user.name").value("免登研发"))
                .andExpect(jsonPath("$.data.user.role").value("RND_ENGINEER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.not("mock-token-ou_oauth_001")))
                .andReturn();

        var accessToken = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/session/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.name").value("免登研发"))
                .andExpect(jsonPath("$.data.feishuUserId").value("ou_oauth_001"))
                .andExpect(jsonPath("$.data.role").value("RND_ENGINEER"));
    }

    @Test
    void feishuOauthCallbackRejectsUnboundFeishuUser() throws Exception {
        mockMvc.perform(post("/api/v1/feishu/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "mock:ou_unbound_001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEISHU_USER_NOT_BOUND"));
    }

    @Test
    void feishuIntegrationStatusExposesClientModeAndConfigurationReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/feishu/integration/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://open.feishu.cn"))
                .andExpect(jsonPath("$.data.appId").value(""))
                .andExpect(jsonPath("$.data.appIdConfigured").value(false))
                .andExpect(jsonPath("$.data.appSecretConfigured").value(false))
                .andExpect(jsonPath("$.data.readyForOpenApi").value(false));
    }

    @Test
    void approvingRequestPersistsProjectVersionAndTaskToDatabase() throws Exception {
        var taskId = createApprovedRequest();

        assertThat(countById("rnd_task", taskId)).isEqualTo(1);
        var versionId = valueById("rnd_task", taskId, "version_id");
        var projectId = valueById("rnd_task", taskId, "project_id");
        var sampleNo = valueById("rnd_task", taskId, "sample_no");

        assertThat(countById("sample_project", projectId)).isEqualTo(1);
        assertThat(countById("sample_version", versionId)).isEqualTo(1);
        assertThat(countBySampleNo("sample_request", sampleNo)).isEqualTo(1);
        assertThat(valueById("sample_version", versionId, "version_code")).isEqualTo("A0");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_ASSIGNMENT");
    }

    @Test
    void assigningAndAcceptingTaskPersistsTaskStatusToDatabase() throws Exception {
        var taskId = createApprovedRequest();

        assignTask(taskId);
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_ACCEPTANCE");
        assertThat(valueById("rnd_task", taskId, "assignee_name")).isEqualTo("张研发");
        assertThat(valueById("rnd_task", taskId, "due_date")).isEqualTo("2026-06-25");
        assertThat(valueById("rnd_task", taskId, "assigned_at")).isNotBlank();
        assertThat(valueById("rnd_task", taskId, "assignee_user_id")).isEqualTo("TEST-ASSIGNEE");

        acceptTask(taskId);
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("SAMPLING");
        assertThat(valueById("rnd_task", taskId, "accepted_at")).isNotBlank();
    }

    @Test
    void assignmentRejectsZeroOrMultipleActiveAccountsWithoutSplittingDatabaseAndCacheAndCanRetry() throws Exception {
        var taskId = createApprovedRequest();

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"不存在研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RND_TASK_ASSIGNEE_NOT_FOUND"));
        assertPendingInDatabaseAndCache(taskId);

        jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values ('DUP-1','dup1','x','重名研发','ou-dup-1','RND_ENGINEER','ACTIVE',current_timestamp,current_timestamp)");
        jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values ('DUP-2','dup2','x','重名研发','ou-dup-2','RND_ENGINEER','ACTIVE',current_timestamp,current_timestamp)");
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"重名研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RND_TASK_ASSIGNEE_AMBIGUOUS"));
        assertPendingInDatabaseAndCache(taskId);

        jdbcTemplate.update("delete from user_account where id = 'DUP-2'");
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"重名研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk());
        assertThat(valueById("rnd_task", taskId, "assignee_user_id")).isEqualTo("DUP-1");
        assertThat(workflowService.taskPool()).extracting(com.lhr.rnd.model.RndTask::id).doesNotContain(taskId);
    }

    @Test
    void assignmentCommitFailureRestoresCacheAndDatabaseForRetry() throws Exception {
        var taskId = createApprovedRequest();
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            workflowService.assignTask(taskId, "张研发", java.time.LocalDate.of(2026, 6, 25));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("injected assignment commit failure"); }
            });
        })).hasMessageContaining("injected assignment commit failure");
        assertPendingInDatabaseAndCache(taskId);

        assignTask(taskId);
        assertThat(valueById("rnd_task", taskId, "assignee_user_id")).isEqualTo("TEST-ASSIGNEE");
    }

    @Test
    void acceptsTaskSavesExperimentDraftAndSubmitsInternalTest() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        var experimentFormId = saveExperimentDraft(taskId);
        submitExperimentForTest(experimentFormId);
    }

    @Test
    void savingExperimentDraftPersistsFormAndMaterialDetailsToDatabase() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        var experimentFormId = saveExperimentDraft(taskId);

        assertThat(countById("experiment_form", experimentFormId)).isEqualTo(1);
        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("DRAFT");
        assertThat(valueById("experiment_form", experimentFormId, "operator_name")).isEqualTo("张研发");
        assertThat(valueById("experiment_form", experimentFormId, "version_code")).isEqualTo("A0");
        assertThat(countByColumn("experiment_material", "experiment_form_id", experimentFormId)).isEqualTo(1);
        assertThat(valueByColumn("experiment_material", "experiment_form_id", experimentFormId, "material_name"))
                .isEqualTo("冻猪大肠头（预煮）");
        assertThat(valueByColumn("experiment_material", "experiment_form_id", experimentFormId, "weight_kg"))
                .isEqualTo("100.0000");
    }

    @Test
    void savingDraftWithoutPrimaryMaterialSucceedsButSubmittingRejectsIt() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        var response = mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "summary": "先保存现场草稿，稍后补主料",
                                  "materials": [
                                    {"stage":"辅料","sequence":1,"materialName":"香辛料","weightKg":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.materials", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var experimentFormId = objectMapper.readTree(response).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_MATERIAL_REQUIRED"));
    }

    @Test
    void savingDraftKeepsPackagingMaterialButExcludesItFromYield() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "materials": [
                                    {"stage":"原料","sequence":1,"materialName":"主料","weightKg":10,"materialCategory":"RAW","primaryMaterial":true},
                                    {"stage":"包材","sequence":2,"materialName":"包装袋","weightKg":1,"materialCategory":"PACKAGING","primaryMaterial":false}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[1].materialCategory").value("PACKAGING"));
    }

    @Test
    void savingDraftAllowsAdditionalRawMaterialWithoutMarkingItPrimary() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "materials": [
                                    {"stage":"原料","sequence":1,"materialName":"主料","weightKg":10,"materialCategory":"RAW","primaryMaterial":true},
                                    {"stage":"原料","sequence":2,"materialName":"额外原料","weightKg":1,"materialCategory":"RAW","primaryMaterial":false}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[1].materialCategory").value("RAW"))
                .andExpect(jsonPath("$.data.materials[1].primaryMaterial").value(false));
    }

    @Test
    void submittingExperimentRevalidatesMaterialCategoriesLoadedFromPersistence() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        jdbcTemplate.update("update experiment_material set material_category = 'PACKAGING' where experiment_form_id = ?", experimentFormId);
        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_MATERIAL_INVALID"));
    }

    @Test
    void submittingExperimentRejectsPrimaryMaterialWithoutPositiveWeight() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveSubmissionValidationDraft(taskId, "0", validProcessSteps(), "8.5", "17");

        submitExperimentForTestExpecting(experimentFormId, "PRIMARY_MATERIAL_WEIGHT_REQUIRED");
    }

    @Test
    void submittingExperimentRejectsDraftWithoutAnEffectiveProcess() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveSubmissionValidationDraft(taskId, "10", "[]", "8.5", "17");

        submitExperimentForTestExpecting(experimentFormId, "EXPERIMENT_PROCESS_REQUIRED");
    }

    @Test
    void submittingExperimentRejectsDraftWithoutFinishedOutputWeight() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveSubmissionValidationDraft(taskId, "10", validProcessSteps(), "null", "17");

        submitExperimentForTestExpecting(experimentFormId, "FINISHED_OUTPUT_WEIGHT_REQUIRED");
    }

    @Test
    void submittingExperimentRejectsDraftWithoutFinishedOutputQuantity() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveSubmissionValidationDraft(taskId, "10", validProcessSteps(), "8.5", "null");

        submitExperimentForTestExpecting(experimentFormId, "FINISHED_OUTPUT_QUANTITY_REQUIRED");
    }

    @Test
    void savingDraftAfterMemoryCacheIsClearedUpdatesExistingCurrentExperiment() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "summary": "重启后继续编辑",
                                  "materials": [
                                    {"stage":"原料","sequence":1,"materialName":"主原料","weightKg":12,"materialCategory":"RAW","primaryMaterial":true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(experimentFormId))
                .andExpect(jsonPath("$.data.summary").value("重启后继续编辑"))
                .andExpect(jsonPath("$.data.materials[0].formulaRatio").value(1.0));

        assertThat(countByColumn("experiment_form", "task_id", taskId)).isEqualTo(1);
        assertThat(valueById("experiment_form", experimentFormId, "summary")).isEqualTo("重启后继续编辑");
        assertThat(countByColumn("experiment_material", "experiment_form_id", experimentFormId)).isEqualTo(1);
    }

    @Test
    void submittingDraftAfterMemoryCacheIsClearedReadsExperimentFromDatabase() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        clearWorkflowServiceMemory();

        var testAssignmentId = submitExperimentForTest(experimentFormId);
        var processRevisionId = valueById("test_assignment", testAssignmentId, "process_revision_id");
        clearWorkflowServiceMemory();

        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("SUBMITTED_FOR_TEST");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_TEST");
        assertThat(valueById("test_assignment", testAssignmentId, "experiment_form_id")).isEqualTo(experimentFormId);
        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTestAssignment.id").value(testAssignmentId))
                .andExpect(jsonPath("$.data.currentTestAssignment.processRevisionId").value(processRevisionId))
                .andExpect(jsonPath("$.data.currentExperimentForm.status").value("SUBMITTED_FOR_TEST"));
    }

    @Test
    void concurrentTestHandoffsAndDecisionsHaveExactlyOneDatabaseWinner() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var formId = saveExperimentDraft(taskId);
        submitFormalProcessRevision(formId, ownerPrincipal());
        var start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> afterGate(start,
                () -> workflowService.submitExperimentForTest(formId, "AUTO_ASSIGN", ownerPrincipal())));
        var second = CompletableFuture.supplyAsync(() -> afterGate(start,
                () -> workflowService.submitExperimentForTest(formId, "AUTO_ASSIGN", ownerPrincipal())));
        start.countDown();
        var handoffResults = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(handoffResults.stream().filter(result -> result instanceof com.lhr.rnd.service.SubmitExperimentForTestResult).count()).isEqualTo(1);
        assertThat(handoffResults.stream().filter(result -> result instanceof BusinessException).count()).isEqualTo(1);
        assertThat(countByColumn("test_assignment", "experiment_form_id", formId)).isEqualTo(1);
        var assignmentId = jdbcTemplate.queryForObject("select id from test_assignment where experiment_form_id = ?", String.class, formId);

        var decisionStart = new CountDownLatch(1);
        var pass = CompletableFuture.supplyAsync(() -> afterGate(decisionStart,
                () -> workflowService.passInternalTest(assignmentId, "伪造姓名", "通过", testerPrincipal())));
        var fail = CompletableFuture.supplyAsync(() -> afterGate(decisionStart,
                () -> workflowService.failInternalTestForResample(assignmentId, "伪造姓名", "打回", testerPrincipal())));
        decisionStart.countDown();
        var decisionResults = List.of(pass.get(10, TimeUnit.SECONDS), fail.get(10, TimeUnit.SECONDS));

        assertThat(decisionResults.stream().filter(result -> result instanceof BusinessException).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from test_record where test_assignment_id = ?", Integer.class, assignmentId)).isEqualTo(1);
        assertThat(valueById("test_assignment", assignmentId, "status")).isIn("PASSED", "FAILED_RESAMPLE");
    }

    @Test
    void passingInternalTestAfterMemoryCacheIsClearedReadsAssignmentFormAndTaskFromDatabase() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);

        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE,
                                new SessionPrincipal("FORGED-SAME-NAME", "forged", "内部测试员", null, "TESTER", Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"伪造同名测试员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ASSIGNMENT_TESTER_MISMATCH"));

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"口味和复热状态通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentForm.status").value("LOCKED"))
                .andExpect(jsonPath("$.data.task.status").value("COMPLETED"));

        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("LOCKED");
        assertThat(valueById("test_assignment", testAssignmentId, "status")).isEqualTo("PASSED");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("COMPLETED");
    }

    @Test
    void legacyPendingTestWithoutFormalRevisionCannotPassOrFailAndLeavesWorkflowUnchanged() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var formId = saveExperimentDraft(taskId);
        var assignmentId = submitExperimentForTest(formId);
        jdbcTemplate.update("update test_assignment set process_revision_id = null where id = ?", assignmentId);
        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", assignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"旧任务通过\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_FORMAL_REVISION_REQUIRED"));

        mockMvc.perform(post("/api/v1/test-assignments/{id}/fail-resample", assignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"旧任务打回\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_FORMAL_REVISION_REQUIRED"));

        assertThat(valueById("test_assignment", assignmentId, "status")).isEqualTo("PENDING_TEST");
        assertThat(valueById("experiment_form", formId, "status")).isEqualTo("SUBMITTED_FOR_TEST");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_TEST");
        assertThat(jdbcTemplate.queryForObject("select count(*) from test_record where test_assignment_id = ?", Integer.class, assignmentId)).isZero();
    }

    @Test
    void archivedTestAssignmentCannotPassOrFailAfterSessionRestart() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var formId = saveExperimentDraft(taskId);
        var assignmentId = submitExperimentForTest(formId);
        jdbcTemplate.update("update test_assignment set status = 'ARCHIVED', archived_at = current_timestamp where id = ?", assignmentId);
        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", assignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"历史任务通过\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ASSIGNMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/test-assignments/{id}/fail-resample", assignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"历史任务打回\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ASSIGNMENT_NOT_FOUND"));

        assertThat(valueById("test_assignment", assignmentId, "status")).isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from test_record where test_assignment_id = ?", Integer.class, assignmentId)).isZero();
    }

    @Test
    void directPassAfterRestartAvoidsExistingTestRecordIds() throws Exception {
        var firstTaskId = createApprovedRequest();
        assignTask(firstTaskId);
        acceptTask(firstTaskId);
        var firstAssignmentId = submitExperimentForTest(saveExperimentDraft(firstTaskId));
        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", firstAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"首个历史记录\"}"))
                .andExpect(status().isOk());
        var existingRecordId = jdbcTemplate.queryForObject(
                "select id from test_record where test_assignment_id = ?", String.class, firstAssignmentId);

        var secondTaskId = createApprovedRequest();
        assignTask(secondTaskId);
        acceptTask(secondTaskId);
        var secondFormId = saveExperimentDraft(secondTaskId);
        var secondAssignmentId = submitExperimentForTest(secondFormId);
        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", secondAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"重启后直接通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("COMPLETED"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from test_record", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select id from test_record where test_assignment_id = ?", String.class, secondAssignmentId))
                .isNotEqualTo(existingRecordId);
    }

    @Test
    void directFailForResampleAfterRestartHydratesVersionAndTaskAndUsesFreeIds() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var formId = saveExperimentDraft(taskId);
        var assignmentId = submitExperimentForTest(formId);
        clearWorkflowServiceMemory();

        mockMvc.perform(post("/api/v1/test-assignments/{id}/fail-resample", assignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"重启后直接打回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testAssignment.status").value("FAILED_RESAMPLE"))
                .andExpect(jsonPath("$.data.nextTask.status").value("PENDING_ACCEPTANCE"));

        assertThat(valueById("test_assignment", assignmentId, "status")).isEqualTo("FAILED_RESAMPLE");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from test_record where test_assignment_id = ?", Integer.class, assignmentId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sample_version", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from rnd_task", Integer.class)).isEqualTo(2);
    }

    @Test
    void submittingExperimentForTestPersistsFormTaskAndAssignmentToDatabase() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        var testAssignmentId = submitExperimentForTest(experimentFormId);

        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("SUBMITTED_FOR_TEST");
        assertThat(valueById("experiment_form", experimentFormId, "submitted_at")).isNotBlank();
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_TEST");
        assertThat(countById("test_assignment", testAssignmentId)).isEqualTo(1);
        assertThat(valueById("test_assignment", testAssignmentId, "experiment_form_id")).isEqualTo(experimentFormId);
        assertThat(valueById("test_assignment", testAssignmentId, "tester_name")).isEqualTo("内部测试员");
        assertThat(valueById("test_assignment", testAssignmentId, "status")).isEqualTo("PENDING_TEST");
    }

    @Test
    void uploadingExperimentAttachmentArchivesFileAndAllowsVersionDownload() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var versionId = valueById("experiment_form", experimentFormId, "version_id");
        var sampleNo = valueById("experiment_form", experimentFormId, "sample_no");
        var photoBytes = "称重照片内容".getBytes(StandardCharsets.UTF_8);

        var archiveFileId = mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile("file", "现场称重.jpg", "image/jpeg", photoBytes))
                        .param("fileName", "现场称重.jpg")
                        .param("category", "WEIGHING_PHOTO")
                        .param("uploadedBy", "张研发")
                        .param("remark", "A0 打样称重照片"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("EXPERIMENT_ATTACHMENT"))
                .andExpect(jsonPath("$.data.businessId").value(experimentFormId))
                .andExpect(jsonPath("$.data.versionId").value(versionId))
                .andExpect(jsonPath("$.data.fileName").value("现场称重.jpg"))
                .andExpect(jsonPath("$.data.fileStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.category").value("WEIGHING_PHOTO"))
                .andExpect(jsonPath("$.data.uploadedBy").value("张研发"))
                .andExpect(jsonPath("$.data.remark").value("A0 打样称重照片"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.fileSize").value(photoBytes.length))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        assertThat(countById("archive_file", archiveFileId)).isEqualTo(1);
        assertThat(valueById("archive_file", archiveFileId, "business_type")).isEqualTo("EXPERIMENT_ATTACHMENT");
        assertThat(valueById("archive_file", archiveFileId, "business_id")).isEqualTo(experimentFormId);
        assertThat(valueById("archive_file", archiveFileId, "version_id")).isEqualTo(versionId);
        assertThat(valueById("archive_file", archiveFileId, "category")).isEqualTo("WEIGHING_PHOTO");
        assertThat(valueById("archive_file", archiveFileId, "uploaded_by")).isEqualTo("张研发");
        assertThat(valueById("archive_file", archiveFileId, "remark")).isEqualTo("A0 打样称重照片");
        assertThat(valueById("archive_file", archiveFileId, "content_type")).isEqualTo("image/jpeg");
        assertThat(valueById("archive_file", archiveFileId, "file_size")).isEqualTo(String.valueOf(photoBytes.length));
        assertThat(valueById("archive_file", archiveFileId, "file_path"))
                .startsWith(sampleNo + "/A0/实验附件/" + archiveFileId + "/")
                .endsWith("/现场称重.jpg");
        var archivedPath = Path.of("target/rnd-archive")
                .resolve(valueById("archive_file", archiveFileId, "file_path"));
        assertThat(Files.exists(archivedPath)).isTrue();

        mockMvc.perform(get("/api/v1/sample-versions/{id}/archive-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(archiveFileId))
                .andExpect(jsonPath("$.data[0].businessType").value("EXPERIMENT_ATTACHMENT"))
                .andExpect(jsonPath("$.data[0].category").value("WEIGHING_PHOTO"))
                .andExpect(jsonPath("$.data[0].uploadedBy").value("张研发"));

        mockMvc.perform(get("/api/v1/archive-files/{id}/download", archiveFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(photoBytes));

        var secondPhotoBytes = "第二次称重照片内容".getBytes(StandardCharsets.UTF_8);
        var secondArchiveFileId = mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile("file", "现场称重.jpg", "image/jpeg", secondPhotoBytes))
                        .param("fileName", "现场称重.jpg"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        assertThat(valueById("archive_file", secondArchiveFileId, "file_path"))
                .isNotEqualTo(valueById("archive_file", archiveFileId, "file_path"));
        mockMvc.perform(get("/api/v1/archive-files/{id}/download", archiveFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(photoBytes));
    }

    @Test
    void rejectsExperimentAttachmentWhenFileIsTooLarge() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var oversizedContent = new byte[10 * 1024 * 1024 + 1];

        mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile("file", "超大照片.jpg", "image/jpeg", oversizedContent))
                        .param("fileName", "超大照片.jpg")
                        .param("category", "PROCESS_PHOTO")
                        .param("uploadedBy", "张研发"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVE_FILE_TOO_LARGE"));
    }

    @Test
    void validatesExperimentAttachmentContentTypeWhitelist() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile(
                                "file",
                                "测试记录.pdf",
                                "application/pdf",
                                "pdf内容".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("fileName", "测试记录.pdf")
                        .param("category", "TEST_ATTACHMENT")
                        .param("uploadedBy", "内部测试员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"));

        mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile(
                                "file",
                                "核价附件.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "excel内容".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("fileName", "核价附件.xlsx")
                        .param("category", "TEST_ATTACHMENT")
                        .param("uploadedBy", "张研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType")
                        .value("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(multipart("/api/v1/experiment-forms/{id}/attachments", experimentFormId)
                        .file(new MockMultipartFile(
                                "file",
                                "异常程序.exe",
                                "application/x-msdownload",
                                "exe内容".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("fileName", "异常程序.exe")
                        .param("category", "PROCESS_PHOTO")
                        .param("uploadedBy", "张研发"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVE_FILE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void passingInternalTestLocksExperimentVersion() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);

        var testRecordId = mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"口味和复热状态通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentForm.status").value("LOCKED"))
                .andExpect(jsonPath("$.data.testAssignment.status").value("PASSED"))
                .andExpect(jsonPath("$.data.task.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"testRecord\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];

        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("LOCKED");
        assertThat(valueById("test_assignment", testAssignmentId, "status")).isEqualTo("PASSED");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("COMPLETED");
        assertThat(countById("test_record", testRecordId)).isEqualTo(1);
        assertThat(valueById("test_record", testRecordId, "test_assignment_id")).isEqualTo(testAssignmentId);
        assertThat(valueById("test_record", testRecordId, "result")).isEqualTo("PASSED");
        assertThat(valueById("test_record", testRecordId, "comment")).isEqualTo("口味和复热状态通过");
    }

    @Test
    void failedInternalTestCreatesNextSampleVersionAndResamplingTask() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);

        var response = mockMvc.perform(post("/api/v1/test-assignments/{id}/fail-resample", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"口感偏硬，需调整卤制时间\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testAssignment.status").value("FAILED_RESAMPLE"))
                .andExpect(jsonPath("$.data.nextVersion.versionCode").value("A1"))
                .andExpect(jsonPath("$.data.nextTask.status").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.nextTask.versionCode").value("A1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var testRecordId = response.split("\\\"testRecord\\\":\\{\\\"id\\\":\\\"")[1].split("\"")[0];
        var nextVersionId = response.split("\\\"nextVersion\\\":\\{\\\"id\\\":\\\"")[1].split("\"")[0];
        var nextTaskId = response.split("\\\"nextTask\\\":\\{\\\"id\\\":\\\"")[1].split("\"")[0];

        assertThat(valueById("test_assignment", testAssignmentId, "status")).isEqualTo("FAILED_RESAMPLE");
        assertThat(valueById("experiment_form", experimentFormId, "status")).isEqualTo("LOCKED");
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("COMPLETED");
        assertThat(countById("test_record", testRecordId)).isEqualTo(1);
        assertThat(valueById("test_record", testRecordId, "result")).isEqualTo("FAILED_RESAMPLE");
        assertThat(valueById("test_record", testRecordId, "comment")).isEqualTo("口感偏硬，需调整卤制时间");
        assertThat(countById("sample_version", nextVersionId)).isEqualTo(1);
        assertThat(valueById("sample_version", nextVersionId, "version_code")).isEqualTo("A1");
        assertThat(valueById("sample_version", nextVersionId, "version_number")).isEqualTo("1");
        assertThat(countById("rnd_task", nextTaskId)).isEqualTo(1);
        assertThat(valueById("rnd_task", nextTaskId, "status")).isEqualTo("PENDING_ACCEPTANCE");
        assertThat(valueById("rnd_task", nextTaskId, "version_code")).isEqualTo("A1");
        assertThat(valueById("rnd_task", nextTaskId, "assignee_user_id")).isEqualTo("TEST-ASSIGNEE");
    }

    @Test
    void passedSampleCanRecordShipmentGeneratePricingFileAndNotifyFinance() throws Exception {
        var versionId = createLockedSampleVersion();

        var shipmentId = mockMvc.perform(post("/api/v1/sample-versions/{id}/shipments", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 6,
                                  "receiverName": "销售内勤",
                                  "trackingNo": "SF202606180001",
                                  "remark": "寄客户确认复热效果"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(versionId))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.trackingNo").value("SF202606180001"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        assertThat(countById("shipment_record", shipmentId)).isEqualTo(1);
        assertThat(valueById("shipment_record", shipmentId, "version_id")).isEqualTo(versionId);
        assertThat(valueById("shipment_record", shipmentId, "status")).isEqualTo("SHIPPED");
        assertThat(valueById("shipment_record", shipmentId, "tracking_no")).isEqualTo("SF202606180001");

        var feedbackId = mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "PASSED",
                                  "comment": "客户确认通过，可以进入核价"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipment.status").value("FEEDBACK_PASSED"))
                .andExpect(jsonPath("$.data.feedback.result").value("PASSED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"feedback\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];

        assertThat(valueById("shipment_record", shipmentId, "status")).isEqualTo("FEEDBACK_PASSED");
        assertThat(countById("customer_feedback", feedbackId)).isEqualTo(1);
        assertThat(valueById("customer_feedback", feedbackId, "shipment_id")).isEqualTo(shipmentId);
        assertThat(valueById("customer_feedback", feedbackId, "result")).isEqualTo("PASSED");
        assertThat(valueById("customer_feedback", feedbackId, "comment")).isEqualTo("客户确认通过，可以进入核价");

        var pricingFileId = generatePricingFile(versionId);

        assertThat(countById("pricing_file", pricingFileId)).isEqualTo(1);
        assertThat(valueById("pricing_file", pricingFileId, "version_id")).isEqualTo(versionId);
        assertThat(valueById("pricing_file", pricingFileId, "pricing_version")).isEqualTo("A0-核价V1");
        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("PENDING_PRICING_REVIEW");
        assertThat(valueById("pricing_file", pricingFileId, "file_name"))
                .matches("500g香卤大肠头-LHYC（核价）原料清单A0 \\d{4}\\.\\d{2}\\.\\d{2}\\.xlsx");
        var generatedFileName = valueById("pricing_file", pricingFileId, "file_name");
        assertThat(countByColumn("archive_file", "business_id", pricingFileId)).isEqualTo(1);
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "business_type")).isEqualTo("PRICING_FILE");
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "version_id")).isEqualTo(versionId);
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_name"))
                .isEqualTo(generatedFileName);
        var archivedSampleNo = valueById("pricing_file", pricingFileId, "sample_no");
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_path"))
                .startsWith("pricing-archives/" + archivedSampleNo + "/A0/pricing/" + pricingFileId + "/attempt-")
                .endsWith(".xlsx");
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_status")).isEqualTo("ARCHIVED");
        var archivedPath = Path.of("target/rnd-archive")
                .resolve(valueByColumn("archive_file", "business_id", pricingFileId, "file_path"));
        assertThat(Files.exists(archivedPath)).isTrue();
        assertThat(Files.size(archivedPath)).isEqualTo(Long.parseLong(valueById("pricing_file", pricingFileId, "content_length")));
        var archiveFileId = valueByColumn("archive_file", "business_id", pricingFileId, "id");

        mockMvc.perform(get("/api/v1/sample-versions/{id}/archive-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(archiveFileId))
                .andExpect(jsonPath("$.data[0].businessType").value("PRICING_FILE"))
                .andExpect(jsonPath("$.data[0].businessId").value(pricingFileId))
                .andExpect(jsonPath("$.data[0].fileName").value(generatedFileName))
                .andExpect(jsonPath("$.data[0].fileStatus").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/archive-files/{id}/download", archiveFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(archivedPath)));

        var encodedPricingFileName = java.net.URLEncoder.encode(generatedFileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        mockMvc.perform(get("/api/v1/pricing-files/{id}/download", pricingFileId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedPricingFileName))
                .andExpect(content().bytes(Files.readAllBytes(archivedPath)));

        approvePricingFile(pricingFileId);
        var financeNotificationId = mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"财务核价员\",\"remark\":\"请按研发核价清单核算报价\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.status").value("FINANCE_NOTIFIED"))
                .andExpect(jsonPath("$.data.notification.recipientName").value("财务核价员"))
                .andExpect(jsonPath("$.data.notification.status").value("SENT"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"notification\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];

        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("FINANCE_NOTIFIED");
        assertThat(countById("finance_notification", financeNotificationId)).isEqualTo(1);
        assertThat(valueById("finance_notification", financeNotificationId, "pricing_file_id")).isEqualTo(pricingFileId);
        assertThat(valueById("finance_notification", financeNotificationId, "recipient_name")).isEqualTo("财务核价员");
        assertThat(valueById("finance_notification", financeNotificationId, "status")).isEqualTo("SENT");
    }

    @Test
    void lockedExperimentCanGeneratePricingWithoutShipmentAndReachFinanceInbox() throws Exception {
        var versionId = createLockedSampleVersion();

        assertThat(countByColumn("shipment_record", "version_id", versionId)).isZero();

        var pricingFileId = generatePricingFile(versionId);
        approvePricingFile(pricingFileId);
        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.status").value("FINANCE_NOTIFIED"));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "FINANCE_NOTIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pricingFileId))
                .andExpect(jsonPath("$.data[0].status").value("FINANCE_NOTIFIED"));
    }

    @Test
    void packagingConfirmationPinsLatestFormalRevisionAndExposesItsPricingSource() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-PRICING-1", 1, "80");

        var pricingFileId = generatePricingFile(versionId);

        assertThat(valueById("pricing_file", pricingFileId, "process_revision_id")).isEqualTo("PREV-PRICING-1");
        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发内勤", "RND_ASSISTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.processRevisionId").value("PREV-PRICING-1"))
                .andExpect(jsonPath("$.data.source.source").value("FORMAL_PROCESS_REVISION"))
                .andExpect(jsonPath("$.data.source.revisionNo").value(2))
                .andExpect(jsonPath("$.data.source.finishedYieldPercent").value(80));
    }

    @Test
    void newPricingUsesLatestFormalRevisionWithoutChangingEarlierPricingLink() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-PRICING-1", 1, "80");
        var firstPricingId = generatePricingFile(versionId);
        var firstPricingBytes = workflowService.downloadPricingFile(firstPricingId).content();

        insertFormalPricingRevision(formId, "PREV-PRICING-2", 2, "70");
        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", firstPricingId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"comment\":\"重新核价\",\"rejectionReason\":\"工艺版本更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRICING_REJECTED"));

        var secondPricingId = generatePricingFile(versionId);
        assertThat(valueById("pricing_file", firstPricingId, "process_revision_id")).isEqualTo("PREV-PRICING-1");
        assertThat(valueById("pricing_file", secondPricingId, "process_revision_id")).isEqualTo("PREV-PRICING-2");
        assertThat(workflowService.downloadPricingFile(firstPricingId).content()).isEqualTo(firstPricingBytes);
        assertPricingWorkbookRevision(workflowService.downloadPricingFile(firstPricingId).content(), "PREV-PRICING-1", "80");
        assertPricingWorkbookRevision(workflowService.downloadPricingFile(secondPricingId).content(), "PREV-PRICING-2", "70");
    }

    @Test
    void targetPricingPinsOnlyItsOwnFormsLatestRevision() throws Exception {
        var firstVersionId = createLockedSampleVersion();
        var secondVersionId = createLockedSampleVersion();
        var firstFormId = valueByColumn("experiment_form", "version_id", firstVersionId, "id");
        var secondFormId = valueByColumn("experiment_form", "version_id", secondVersionId, "id");
        insertFormalPricingRevision(firstFormId, "PREV-FIRST-1", 1, "90");
        insertFormalPricingRevision(firstFormId, "PREV-FIRST-2", 2, "80");
        insertFormalPricingRevision(secondFormId, "PREV-SECOND-8", 8, "60");
        insertFormalPricingRevision(secondFormId, "PREV-SECOND-9", 9, "50");

        var pricingFileId = generatePricingFile(firstVersionId);

        assertThat(valueById("pricing_file", pricingFileId, "process_revision_id")).isEqualTo("PREV-FIRST-2");
        assertPricingWorkbookRevision(workflowService.downloadPricingFile(pricingFileId).content(), "PREV-FIRST-2", "80");
    }

    @Test
    void tamperedSnapshotAbortsPackagingConfirmationWithoutChangingAnyState() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-TAMPERED", 1, "80");
        var pricingFileId = createPricingDraft(versionId);
        var items = workflowService.pricingPackagingItems(pricingFileId);
        var packagingBefore = pricingPackagingRows(pricingFileId);
        var filesBefore = pricingArchiveFiles(versionId);
        var cachedBefore = cachedPricingFile(pricingFileId);
        var snapshot = valueById("experiment_process_revision", "PREV-TAMPERED", "snapshot_json");
        jdbcTemplate.update("update experiment_process_revision set snapshot_json = ? where id = ?", snapshot + " ", "PREV-TAMPERED");

        mockMvc.perform(put("/api/v1/pricing-files/{id}/packaging-items", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", modifiedQuantity(items)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR"));

        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("DRAFT_PACKAGING");
        assertThat(valueById("pricing_file", pricingFileId, "process_revision_id")).isNull();
        assertThat(pricingPackagingRows(pricingFileId)).isEqualTo(packagingBefore);
        assertThat(countByColumn("archive_file", "business_id", pricingFileId)).isZero();
        assertThat(countByColumn("pricing_archive_cleanup_ledger", "pricing_file_id", pricingFileId)).isZero();
        assertThat(pricingArchiveFiles(versionId)).isEqualTo(filesBefore);
        assertThat(cachedPricingFile(pricingFileId)).isEqualTo(cachedBefore);
    }

    @Test
    void rolledBackPackagingConfirmationLeavesDatabaseCacheAndArchiveUnchanged() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-ROLLBACK", 1, "80");
        ReflectionTestUtils.setField(workflowService, "pricingFileSequence",
                100_000 + (int) (System.nanoTime() % 800_000));
        var pricingFileId = createPricingDraft(versionId);
        var items = workflowService.pricingPackagingItems(pricingFileId);
        var packagingBefore = pricingPackagingRows(pricingFileId);
        var filesBefore = pricingArchiveFiles(versionId);
        var cachedBefore = cachedPricingFile(pricingFileId);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            workflowService.confirmPricingPackaging(pricingFileId, items, "张研发", "RND_ENGINEER");
            transaction.setRollbackOnly();
        });

        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("DRAFT_PACKAGING");
        assertThat(valueById("pricing_file", pricingFileId, "process_revision_id")).isNull();
        assertThat(pricingPackagingRows(pricingFileId)).isEqualTo(packagingBefore);
        assertThat(countByColumn("archive_file", "business_id", pricingFileId)).isZero();
        assertThat(countByColumn("pricing_archive_cleanup_ledger", "pricing_file_id", pricingFileId)).isZero();
        assertThat(pricingArchiveFiles(versionId)).isEqualTo(filesBefore);
        assertThat(cachedPricingFile(pricingFileId)).isEqualTo(cachedBefore);
    }

    @RepeatedTest(3)
    void rolledBackAttemptCleanupCannotDeleteCommittedRetryBytes() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-ATTEMPT-A", 1, "80");
        var pricingFileId = createPricingDraft(versionId);
        var items = workflowService.pricingPackagingItems(pricingFileId);
        var firstStored = new CountDownLatch(1);
        var releaseFirstStore = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var firstCleanupPaused = new CountDownLatch(1);
        var releaseFirstCleanup = new CountDownLatch(1);
        var stores = new AtomicInteger();
        var firstDeletes = new AtomicInteger();
        var firstKey = new AtomicReference<String>();
        var secondKey = new AtomicReference<String>();
        doAnswer(invocation -> {
            var key = invocation.getArgument(0, String.class);
            var result = invocation.callRealMethod();
            if (stores.incrementAndGet() == 1) {
                firstKey.set(key);
                firstStored.countDown();
                if (!releaseFirstStore.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("first archive store pause timed out");
                }
            } else {
                secondKey.compareAndSet(null, key);
            }
            return result;
        }).when(archiveStorageService).store(anyString(), any());
        doAnswer(invocation -> {
            if (invocation.getArgument(0, String.class).equals(firstKey.get())) {
                firstCleanupPaused.countDown();
                if (!releaseFirstCleanup.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("first archive cleanup pause timed out");
                }
                if (firstDeletes.incrementAndGet() == 1) {
                    throw new BusinessException("ARCHIVE_FILE_DELETE_FAILED", "injected first-attempt delete failure");
                }
            }
            return invocation.callRealMethod();
        }).when(archiveStorageService).delete(anyString());

        var first = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    workflowService.confirmPricingPackaging(pricingFileId, items, "张研发", "RND_ENGINEER");
                    status.setRollbackOnly();
                }));
        assertThat(firstStored.await(20, TimeUnit.SECONDS)).isTrue();
        insertFormalPricingRevision(formId, "PREV-ATTEMPT-B", 2, "70");
        var second = CompletableFuture.supplyAsync(() -> {
            secondStarted.countDown();
            return workflowService.confirmPricingPackaging(pricingFileId, items, "张研发", "RND_ENGINEER");
        });
        assertThat(secondStarted.await(20, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
        releaseFirstStore.countDown();
        assertThat(firstCleanupPaused.await(20, TimeUnit.SECONDS)).isTrue();
        try {
            var committed = second.get(20, TimeUnit.SECONDS);
            assertThat(committed.processRevisionId()).isEqualTo("PREV-ATTEMPT-B");
            assertThat(secondKey.get()).isNotEqualTo(firstKey.get());
            assertThat(secondKey.get()).startsWith("pricing-archives/").endsWith(".xlsx");
            assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_path"))
                    .isEqualTo(secondKey.get());
            assertPricingPersistenceCoherent(pricingFileId, items, "PREV-ATTEMPT-B", "70");
            assertThat(Files.exists(pricingArchivePath(secondKey.get()))).isTrue();
        } finally {
            releaseFirstCleanup.countDown();
            first.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject(
                "select state from pricing_archive_cleanup_ledger where storage_key = ?",
                String.class, firstKey.get())).isEqualTo("ORPHANED");
        assertThat(jdbcTemplate.queryForObject(
                "select error from pricing_archive_cleanup_ledger where storage_key = ?",
                String.class, firstKey.get())).contains("injected first-attempt delete failure");
        assertThat(Files.exists(pricingArchivePath(firstKey.get()))).isTrue();

        pricingArchiveCleanupLedger.reconcileStale();

        assertThat(countByColumn("pricing_archive_cleanup_ledger", "storage_key", firstKey.get())).isZero();
        assertThat(Files.exists(pricingArchivePath(firstKey.get()))).isFalse();
        assertThat(Files.exists(pricingArchivePath(secondKey.get()))).isTrue();
        assertPricingPersistenceCoherent(pricingFileId, items, "PREV-ATTEMPT-B", "70");
    }

    @Test
    void failedCommitCallbackLeavesReferencedAttemptForSafeReconciliation() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-CALLBACK", 1, "80");
        doThrow(new IllegalStateException("injected pricing ledger confirm failure"))
                .doCallRealMethod()
                .when(pricingArchiveCleanupLedger)
                .confirm(any(com.lhr.rnd.service.PricingArchiveCleanupLedgerService.Reservation.class));

        var pricingFileId = generatePricingFile(versionId);
        var storageKey = valueByColumn("archive_file", "business_id", pricingFileId, "file_path");

        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("PENDING_PRICING_REVIEW");
        assertThat(jdbcTemplate.queryForObject(
                "select state from pricing_archive_cleanup_ledger where storage_key = ?",
                String.class, storageKey)).isEqualTo("RESERVED");
        assertThat(Files.exists(pricingArchivePath(storageKey))).isTrue();
        jdbcTemplate.update("update pricing_archive_cleanup_ledger set lease_until = ? where storage_key = ?",
                LocalDateTime.now().minusHours(2), storageKey);

        pricingArchiveCleanupLedger.reconcileStale();

        assertThat(countByColumn("pricing_archive_cleanup_ledger", "storage_key", storageKey)).isZero();
        assertThat(Files.exists(pricingArchivePath(storageKey))).isTrue();
        var archiveId = valueByColumn("archive_file", "business_id", pricingFileId, "id");
        assertThat(workflowService.downloadArchiveFile(archiveId).content())
                .isEqualTo(workflowService.downloadPricingFile(pricingFileId).content());
        assertPricingWorkbookRevision(workflowService.downloadPricingFile(pricingFileId).content(),
                "PREV-CALLBACK", "80");
    }

    @Test
    void pricingArchiveReconcileHonorsLeaseOwnerAndSafePrefix() throws Exception {
        var versionId = createLockedSampleVersion();
        var pricingFileId = createPricingDraft(versionId);
        var reservation = pricingArchiveCleanupLedger.register(pricingFileId);
        archiveStorageService.store(reservation.storageKey(), new byte[]{1, 2, 3});
        var staleOwner = new com.lhr.rnd.service.PricingArchiveCleanupLedgerService.Reservation(
                pricingFileId, reservation.storageKey(), "stale-owner-token", reservation.leaseUntil());

        pricingArchiveCleanupLedger.confirm(staleOwner);
        pricingArchiveCleanupLedger.orphanAndDelete(staleOwner);
        pricingArchiveCleanupLedger.reconcileStale();

        assertThat(jdbcTemplate.queryForObject(
                "select state from pricing_archive_cleanup_ledger where storage_key = ?",
                String.class, reservation.storageKey())).isEqualTo("RESERVED");
        assertThat(Files.exists(pricingArchivePath(reservation.storageKey()))).isTrue();
        assertThatThrownBy(() -> pricingArchiveCleanupLedger.register(
                pricingFileId, "process-artifacts/not-a-pricing-attempt.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe pricing archive cleanup key");
        jdbcTemplate.update("update pricing_archive_cleanup_ledger set lease_until = ? where storage_key = ?",
                LocalDateTime.now().minusHours(2), reservation.storageKey());

        pricingArchiveCleanupLedger.reconcileOnStartup();

        assertThat(countByColumn("pricing_archive_cleanup_ledger", "storage_key", reservation.storageKey())).isZero();
        assertThat(Files.exists(pricingArchivePath(reservation.storageKey()))).isFalse();
    }

    @Test
    void formalRevisionWithUnavailableYieldLeavesWorkbookAndDetailNull() throws Exception {
        var versionId = createLockedSampleVersion();
        var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
        insertFormalPricingRevision(formId, "PREV-YIELD-NULL", 1, null, "NONE");

        var pricingFileId = generatePricingFile(versionId);

        var bytes = workflowService.downloadPricingFile(pricingFileId).content();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(8).getCell(6).getStringCellValue()).contains("finishedYield=UNAVAILABLE");
            assertThat(sheet.getRow(15).getCell(6).getCellType()).isEqualTo(CellType.BLANK);
        }
        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发内勤", "RND_ASSISTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source.finishedYieldPercent").value(nullValue()));
    }

    @Test
    void concurrentPackagingConfirmationIsDatabaseLinearizedForThreeRounds() throws Exception {
        for (int round = 1; round <= 3; round++) {
            var currentRound = round;
            var versionId = createLockedSampleVersion();
            var formId = valueByColumn("experiment_form", "version_id", versionId, "id");
            insertFormalPricingRevision(formId, "PREV-RACE-" + currentRound + "-1", 1, "80");
            insertFormalPricingRevision(formId, "PREV-RACE-" + currentRound + "-2", 2, "70");
            var pricingFileId = createPricingDraft(versionId);
            var items = workflowService.pricingPackagingItems(pricingFileId);
            var ready = new CountDownLatch(3);
            var go = new CountDownLatch(1);
            var first = CompletableFuture.supplyAsync(() -> confirmTogether(pricingFileId, items, ready, go));
            var second = CompletableFuture.supplyAsync(() -> confirmTogether(pricingFileId, items, ready, go));
            var nextRevision = CompletableFuture.runAsync(() -> insertRevisionTogether(
                    formId, "PREV-RACE-" + currentRound + "-3", 3, "60", ready, go));
            ready.await();
            go.countDown();

            var outcomes = List.of(first.join(), second.join());
            nextRevision.join();
            assertThat(outcomes).filteredOn("SUCCESS"::equals).hasSize(1);
            assertThat(outcomes).filteredOn("PRICING_PACKAGING_CONFIRM_ILLEGAL"::equals).hasSize(1);
            var pinnedRevision = valueById("pricing_file", pricingFileId, "process_revision_id");
            assertThat(pinnedRevision).isIn("PREV-RACE-" + currentRound + "-2", "PREV-RACE-" + currentRound + "-3");
            assertPricingPersistenceCoherent(pricingFileId, items, pinnedRevision,
                    pinnedRevision.endsWith("-3") ? "60" : "70");
        }
    }

    @Test
    void listsLockedVersionsReadyForPricingWithoutShipment() throws Exception {
        var versionId = createLockedSampleVersion();

        mockMvc.perform(get("/api/v1/sample-versions/pricing-ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].versionId").value(versionId))
                .andExpect(jsonPath("$.data[0].versionCode").value("A0"));

        generatePricingFile(versionId);

        mockMvc.perform(get("/api/v1/sample-versions/pricing-ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void financeCanAcknowledgeNotifiedPricingFileIdempotently() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());
        approvePricingFile(pricingFileId);
        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/receive", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("钱财务", "FINANCE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\":\"钱财务\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINANCE_RECEIVED"));

        var receivedAt = valueById("pricing_file", pricingFileId, "received_at");
        mockMvc.perform(post("/api/v1/pricing-files/{id}/receive", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("钱财务", "FINANCE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\":\"钱财务\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINANCE_RECEIVED"));

        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("FINANCE_RECEIVED");
        assertThat(valueById("pricing_file", pricingFileId, "received_by")).isEqualTo("钱财务");
        assertThat(valueById("pricing_file", pricingFileId, "received_at")).isEqualTo(receivedAt);
    }

    @Test
    void approvedPricingIsAutomaticallyHandedToFinance() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_REVIEW_REQUIRED"));

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("其他研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"其他研发\",\"comment\":\"核价无误\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_REVIEW_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"张研发\",\"comment\":\"配方与出成数据确认无误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINANCE_NOTIFIED"))
                .andExpect(jsonPath("$.data.reviewedBy").value("张研发"));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "FINANCE_NOTIFIED")
                        .requestAttr("sessionPrincipal", principal("钱财务", "FINANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(pricingFileId));

        var reviewedAt = valueById("pricing_file", pricingFileId, "reviewed_at");
        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"张研发\",\"comment\":\"重复提交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINANCE_NOTIFIED"));
        assertThat(valueById("pricing_file", pricingFileId, "reviewed_at")).isEqualTo(reviewedAt);
        assertThat(countByColumn("finance_notification", "pricing_file_id", pricingFileId)).isEqualTo(1);
    }

    @Test
    void pricingReviewRequiresServerSessionPrincipalInsteadOfClientReviewerName() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"张研发\",\"comment\":\"伪造审核人\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_PRINCIPAL_REQUIRED"));

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"伪造审核人\",\"comment\":\"核价无误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewedBy").value("张研发"));
    }

    @Test
    void pricingNotificationAndReceiptUseServerSessionPrincipal() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_PRINCIPAL_REQUIRED"));

        approvePricingFile(pricingFileId);
        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/receive", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("钱财务", "FINANCE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\":\"伪造接收人\"}"))
                .andExpect(status().isOk());
        assertThat(valueById("pricing_file", pricingFileId, "received_by")).isEqualTo("钱财务");
    }

    @Test
    void productOwnerCanOnlyReadOwnPricingFiles() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());

        mockMvc.perform(get("/api/v1/pricing-files")
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pricingFileId));
        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.id").value(pricingFileId));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .requestAttr("sessionPrincipal", principal("其他研发", "RND_ENGINEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("其他研发", "RND_ENGINEER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_RND_ENGINEER"));
    }

    @Test
    void productOwnerCanOnlyExportArchiveAndDownloadOwnPricingFiles() throws Exception {
        var ownVersionId = createLockedSampleVersion();
        var otherVersionId = createLockedSampleVersion("李研发");
        var ownPricingFileId = generatePricingFile(ownVersionId);
        var otherPricingFileId = generatePricingFile(otherVersionId);
        var ownArchiveFileId = valueByColumn("archive_file", "business_id", ownPricingFileId, "id");
        var otherArchiveFileId = valueByColumn("archive_file", "business_id", otherPricingFileId, "id");
        var owner = principal("张研发", "RND_ENGINEER");

        mockMvc.perform(get("/api/v1/pricing-files/{id}/download", ownPricingFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/pricing-files/{id}/export", ownPricingFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sample-versions/{id}/archive-files", ownVersionId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].businessId").value(ownPricingFileId));
        mockMvc.perform(get("/api/v1/archive-files/{id}/download", ownArchiveFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pricing-files/{id}/download", otherPricingFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_RND_ENGINEER"));
        mockMvc.perform(get("/api/v1/reports/pricing-files/{id}/export", otherPricingFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_RND_ENGINEER"));
        mockMvc.perform(get("/api/v1/sample-versions/{id}/archive-files", otherVersionId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/v1/archive-files/{id}/download", otherArchiveFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_RND_ENGINEER"));
    }

    @Test
    void financeCannotBypassPricingReviewThroughExportsOrArchives() throws Exception {
        var versionId = createLockedSampleVersion();
        var pricingFileId = generatePricingFile(versionId);
        var archiveFileId = valueByColumn("archive_file", "business_id", pricingFileId, "id");
        jdbcTemplate.update(
                """
                        insert into archive_file (
                            id, business_type, business_id, version_id, file_name, file_path,
                            file_status, archived_at
                        ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "ARCH-NON-PRICING-001",
                "EXPERIMENT_ATTACHMENT",
                "FORM-NON-PRICING-001",
                versionId,
                "实验照片.jpg",
                "attachments/non-pricing.jpg",
                "ARCHIVED"
        );
        var finance = principal("钱财务", "FINANCE");

        mockMvc.perform(get("/api/v1/reports/pricing-files/{id}/export", pricingFileId)
                        .requestAttr("sessionPrincipal", finance))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_FINANCE"));
        mockMvc.perform(get("/api/v1/sample-versions/{id}/archive-files", versionId)
                        .requestAttr("sessionPrincipal", finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].businessType").value("EXPERIMENT_ATTACHMENT"));
        mockMvc.perform(get("/api/v1/archive-files/{id}/download", archiveFileId)
                        .requestAttr("sessionPrincipal", finance))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_FINANCE"));
    }

    @Test
    void notifyingFinanceIsIdempotentAfterTheFirstSuccessfulHandoff() throws Exception {
        var pricingFileId = generatePricingFile(createLockedSampleVersion());
        approvePricingFile(pricingFileId);

        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"钱财务\",\"remark\":\"请接收核价文件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.status").value("FINANCE_NOTIFIED"));
        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("赵内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"另一位财务\",\"remark\":\"重复请求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.status").value("FINANCE_NOTIFIED"))
                .andExpect(jsonPath("$.data.notification.recipientName").value("财务核价员"));

        assertThat(countByColumn("finance_notification", "pricing_file_id", pricingFileId)).isEqualTo(1);
    }

    @Test
    void pricingRejectionRequiresReasonAndCreatesNextPricingVersion() throws Exception {
        var versionId = createLockedSampleVersion();
        var firstPricingFileId = generatePricingFile(versionId);

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", firstPricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发总监", "RND_DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reviewerName\":\"研发总监\",\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", firstPricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发总监", "RND_DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reviewerName\":\"研发总监\",\"comment\":\"核价原料规格有误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRICING_REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("核价原料规格有误"));

        var secondPricingFileId = generatePricingFile(versionId);
        assertThat(valueById("pricing_file", secondPricingFileId, "pricing_version")).isEqualTo("A0-核价V2");

        assertThat(valueById("pricing_file", firstPricingFileId, "status")).isEqualTo("PRICING_REJECTED");
    }

    @Test
    void rejectsPricingFileBeforeExperimentVersionIsLocked() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        saveExperimentDraft(taskId);
        var versionId = taskId.replace("TASK", "VER");

        mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_VERSION_NOT_READY_FOR_PRICING"));
    }

    @Test
    void generatingPricingFileUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var versionId = createLockedSampleVersion();
        disableWorkflowAction("SAMPLE_COMPLETED", "REQUEST_PRICING", "PRICING_FILE_GENERATED");

        mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void generatingPricingFileArchiveUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var versionId = createLockedSampleVersion();
        disableWorkflowAction("FINANCE_NOTIFIED", "ARCHIVE", "ARCHIVED");

        mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void pricingApprovalUsesWorkflowConfigBeforeAutomaticFinanceHandoff() throws Exception {
        var versionId = createLockedSampleVersion();
        var pricingFileId = generatePricingFile(versionId);
        disableWorkflowAction("PENDING_PRICING_REVIEW", "APPROVE_PRICING", "PRICING_APPROVED");

        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"请核价\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void rejectsShipmentBeforeExperimentVersionIsLocked() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        saveExperimentDraft(taskId);
        var versionId = taskId.replace("TASK", "VER");

        mockMvc.perform(post("/api/v1/sample-versions/{id}/shipments", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 6,
                                  "receiverName": "销售内勤",
                                  "trackingNo": "SF202606180002",
                                  "remark": "未锁版寄样"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_VERSION_NOT_READY_FOR_SHIPMENT"));
    }

    @Test
    void customerFeedbackPassedUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var shipmentId = createShipment(createLockedSampleVersion());
        disableWorkflowAction("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_PASS", "SAMPLE_COMPLETED");

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "PASSED",
                                  "comment": "客户确认通过"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void customerFeedbackResampleUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var shipmentId = createShipment(createLockedSampleVersion());
        disableWorkflowAction("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_RESAMPLE", "RESAMPLING_REQUIRED");

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "FAILED_RESAMPLE",
                                  "comment": "客户要求调整辣度后复打样"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void customerFeedbackStoppedUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var shipmentId = createShipment(createLockedSampleVersion());
        disableWorkflowAction("SAMPLE_COMPLETED", "CUSTOMER_FEEDBACK_STOP", "STOPPED");

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "STOPPED",
                                  "comment": "客户项目暂停"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void customerFeedbackResampleCreatesNextSampleVersionAndRndTask() throws Exception {
        var versionId = createLockedSampleVersion();
        var projectId = valueById("sample_version", versionId, "project_id");
        var shipmentId = createShipment(versionId);

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "FAILED_RESAMPLE",
                                  "comment": "客户要求调整辣度后复打样"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipment.status").value("FEEDBACK_FAILED_RESAMPLE"))
                .andExpect(jsonPath("$.data.feedback.result").value("FAILED_RESAMPLE"));

        assertThat(countByColumn("sample_version", "project_id", projectId)).isEqualTo(2);
        assertThat(valueByColumns("sample_version", "project_id", projectId, "version_number", "1", "version_code"))
                .isEqualTo("A1");
        var nextVersionId = valueByColumns("sample_version", "project_id", projectId, "version_number", "1", "id");
        assertThat(valueByColumn("rnd_task", "version_id", nextVersionId, "status"))
                .isEqualTo("PENDING_ACCEPTANCE");
        assertThat(valueByColumn("rnd_task", "version_id", nextVersionId, "version_code"))
                .isEqualTo("A1");
        assertThat(valueByColumn("rnd_task", "version_id", nextVersionId, "assignee_user_id"))
                .isEqualTo("TEST-ASSIGNEE");
    }

    private void assertPendingInDatabaseAndCache(String taskId) {
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(valueById("rnd_task", taskId, "assignee_user_id")).isBlank();
        assertThat(workflowService.taskPool()).extracting(com.lhr.rnd.model.RndTask::id).contains(taskId);
    }

    @Test
    void customerFeedbackStoppedMarksSampleProjectStopped() throws Exception {
        var versionId = createLockedSampleVersion();
        var projectId = valueById("sample_version", versionId, "project_id");
        var shipmentId = createShipment(versionId);

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "STOPPED",
                                  "comment": "客户项目暂停"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipment.status").value("STOPPED"))
                .andExpect(jsonPath("$.data.feedback.result").value("STOPPED"));

        assertThat(valueById("sample_project", projectId, "status")).isEqualTo("STOPPED");
    }

    @Test
    void stoppedSampleProjectsReturnStopReasonAndLastVersion() throws Exception {
        var versionId = createLockedSampleVersion();
        var projectId = valueById("sample_version", versionId, "project_id");
        var shipmentId = createShipment(versionId);

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "STOPPED",
                                  "comment": "客户项目暂停"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sample-projects/stopped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectId").value(projectId))
                .andExpect(jsonPath("$.data[0].productName").value("500g香卤大肠头"))
                .andExpect(jsonPath("$.data[0].lastVersionCode").value("A0"))
                .andExpect(jsonPath("$.data[0].stoppedBy").value("业务员"))
                .andExpect(jsonPath("$.data[0].stopReason").value("客户项目暂停"));
    }

    @Test
    void dashboardOverviewSummarizesWorkflowCountsAndRecentTasks() throws Exception {
        createPendingSampleRequest();
        var pendingTaskId = createApprovedRequest();
        var assignedTaskId = createApprovedRequest();
        assignTask(assignedTaskId, "李研发");
        var samplingTaskId = createApprovedRequest();
        assignTask(samplingTaskId, "张研发");
        acceptTask(samplingTaskId);
        var completedVersionId = createLockedSampleVersion();
        var pricingFileId = generatePricingFile(completedVersionId);
        var stoppedVersionId = createLockedSampleVersion();
        var stoppedShipmentId = createShipment(stoppedVersionId);
        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", stoppedShipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "STOPPED",
                                  "comment": "客户项目暂停"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingReviewCount").value(1))
                .andExpect(jsonPath("$.data.pendingAssignmentCount").value(1))
                .andExpect(jsonPath("$.data.pendingAcceptanceCount").value(1))
                .andExpect(jsonPath("$.data.samplingCount").value(1))
                .andExpect(jsonPath("$.data.pendingPricingCount").value(1))
                .andExpect(jsonPath("$.data.financeNotifiedCount").value(0))
                .andExpect(jsonPath("$.data.stoppedCount").value(1))
                .andExpect(jsonPath("$.data.recentTasks", hasSize(3)))
                .andExpect(jsonPath("$.data.recentTasks[0].taskId").value(samplingTaskId))
                .andExpect(jsonPath("$.data.recentTasks[0].status").value("SAMPLING"))
                .andExpect(jsonPath("$.data.recentTasks[1].taskId").value(assignedTaskId))
                .andExpect(jsonPath("$.data.recentTasks[1].assigneeName").value("李研发"))
                .andExpect(jsonPath("$.data.recentTasks[2].taskId").value(pendingTaskId))
                .andExpect(jsonPath("$.data.pendingPricingFiles", hasSize(1)))
                .andExpect(jsonPath("$.data.pendingPricingFiles[0].pricingFileId").value(pricingFileId));
    }

    @Test
    void listEndpointsFilterByStatusAndKeywordForDashboardDrillDown() throws Exception {
        createPendingSampleRequest();
        var pendingTaskId = createApprovedRequest();
        var assignedTaskId = createApprovedRequest();
        assignTask(assignedTaskId, "李研发");
        var samplingTaskId = createApprovedRequest();
        assignTask(samplingTaskId, "张研发");
        acceptTask(samplingTaskId);
        var completedVersionId = createLockedSampleVersion();
        var pricingFileId = generatePricingFile(completedVersionId);

        mockMvc.perform(get("/api/v1/sample-requests")
                        .param("status", "PENDING_REVIEW")
                        .param("keyword", "香卤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data[0].productName").value("500g香卤大肠头"));

        mockMvc.perform(get("/api/v1/rnd-tasks")
                        .param("status", "SAMPLING")
                        .param("keyword", "香卤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(samplingTaskId))
                .andExpect(jsonPath("$.data[0].status").value("SAMPLING"));

        mockMvc.perform(get("/api/v1/rnd-tasks")
                        .param("status", "PENDING_ASSIGNMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pendingTaskId));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "PENDING_PRICING_REVIEW")
                        .param("keyword", "香卤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pricingFileId))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PRICING_REVIEW"));
    }

    @Test
    void listEndpointsSupportPaginationAndSortingWhenPageParametersAreProvided() throws Exception {
        createPendingSampleRequest();
        var secondRequestId = createPendingSampleRequest();
        var thirdRequestId = createPendingSampleRequest();

        mockMvc.perform(get("/api/v1/sample-requests")
                        .param("status", "PENDING_REVIEW")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(thirdRequestId))
                .andExpect(jsonPath("$.data.items[1].id").value(secondRequestId));

        mockMvc.perform(get("/api/v1/sample-requests")
                        .param("status", "PENDING_REVIEW")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value("REQ-0001"))
                .andExpect(jsonPath("$.data.items[1].id").value(secondRequestId));

        var firstTaskId = createApprovedRequest();
        var secondTaskId = createApprovedRequest();
        var thirdTaskId = createApprovedRequest();

        mockMvc.perform(get("/api/v1/rnd-tasks")
                        .param("status", "PENDING_ASSIGNMENT")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(thirdTaskId))
                .andExpect(jsonPath("$.data.items[1].id").value(secondTaskId));

        var firstPricingId = generatePricingFile(createLockedSampleVersion());
        var secondPricingId = generatePricingFile(createLockedSampleVersion());

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "PENDING_PRICING_REVIEW")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(secondPricingId));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "PENDING_PRICING_REVIEW")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "generatedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(firstPricingId));
    }

    @Test
    void rndTaskDetailReturnsRoleAwareFieldGroupsAndAvailableActions() throws Exception {
        var taskId = createApprovedRequest();

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.id").value(taskId))
                .andExpect(jsonPath("$.data.version.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.fieldGroups[0].title").value("基础信息"))
                .andExpect(jsonPath("$.data.fieldGroups[0].fields", hasItems("applicationScenario", "flavorRequirement", "customerName", "productType")))
                .andExpect(jsonPath("$.data.fieldGroups[1].title").value("任务信息"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("ASSIGN_TASK"))
                .andExpect(jsonPath("$.data.availableActions[0].endpoint").value("/api/v1/rnd-tasks/" + taskId + "/assign"));

        assignTask(taskId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER")
                        .param("operatorName", "张研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("ACCEPT_TASK"));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER")
                        .param("operatorName", "李研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(0)));

        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER")
                        .param("operatorName", "张研发"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SAMPLING"))
                .andExpect(jsonPath("$.data.currentExperimentForm.id").value(experimentFormId))
                .andExpect(jsonPath("$.data.fieldGroups[2].title").value("实验单"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(2)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("OPEN_EXPERIMENT_FORM"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("SUBMIT_EXPERIMENT_TEST"));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ASSISTANT")
                        .param("operatorName", "赵内勤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(0)));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_DIRECTOR")
                        .param("operatorName", "赵总监"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(2)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("OPEN_EXPERIMENT_FORM"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("SUBMIT_EXPERIMENT_TEST"));

        var testAssignmentId = submitExperimentForTest(experimentFormId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "TESTER")
                        .param("operatorName", "内部测试员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PENDING_TEST"))
                .andExpect(jsonPath("$.data.currentTestAssignment.id").value(testAssignmentId))
                .andExpect(jsonPath("$.data.availableActions", hasSize(2)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("PASS_INTERNAL_TEST"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("FAIL_RESAMPLE"));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "TESTER")
                        .param("operatorName", "其他测试员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(0)));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(0)));
    }

    @Test
    void shipmentAndPricingDetailsReturnRoleAwareAvailableActions() throws Exception {
        var versionId = createLockedSampleVersion();
        var shipmentId = createShipment(versionId);

        mockMvc.perform(get("/api/v1/shipments/{id}/detail", shipmentId)
                        .param("role", "RND_ASSISTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipment.id").value(shipmentId))
                .andExpect(jsonPath("$.data.version.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.fieldGroups[0].title").value("寄样信息"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(3)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("CUSTOMER_FEEDBACK_PASS"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("CUSTOMER_FEEDBACK_RESAMPLE"))
                .andExpect(jsonPath("$.data.availableActions[2].code").value("CUSTOMER_FEEDBACK_STOP"));

        mockMvc.perform(post("/api/v1/shipments/{id}/feedback", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackBy": "业务员",
                                  "result": "PASSED",
                                  "comment": "客户确认通过，可以核价"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shipments/{id}/detail", shipmentId)
                        .param("role", "RND_ASSISTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerFeedback.result").value("PASSED"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("GENERATE_PRICING_FILE"));

        var pricingFileId = generatePricingFile(versionId);

        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发内勤", "RND_ASSISTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingFile.id").value(pricingFileId))
                .andExpect(jsonPath("$.data.pricingFile.source").value("FORMAL_PROCESS_REVISION"))
                .andExpect(jsonPath("$.data.source.source").value("FORMAL_PROCESS_REVISION"))
                .andExpect(jsonPath("$.data.version.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.fieldGroups[0].title").value("核价文件"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("DOWNLOAD_PRICING_FILE"));

        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("钱财务", "FINANCE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICING_FILE_NOT_AVAILABLE_FOR_FINANCE"));

        approvePricingFile(pricingFileId);

        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发内勤", "RND_ASSISTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"财务核价员\",\"remark\":\"请核价\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pricing-files/{id}/detail", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("研发内勤", "RND_ASSISTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financeNotification.recipientName").value("财务核价员"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("DOWNLOAD_PRICING_FILE"));
    }

    private void acceptTask(String taskId) throws Exception {
        acceptTask(taskId, "张研发");
    }

    private void acceptTask(String taskId, String acceptedBy) throws Exception {
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"%s\"}".formatted(acceptedBy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAMPLING"));
    }

    private String saveExperimentDraft(String taskId) throws Exception {
        var draftJson = """
                {
                  "operatorName": "张研发",
                  "summary": "A0 首版打样，卤制后冷却包装",
                  "materials": [
                    {
                      "stage": "清洗",
                      "sequence": 1,
                      "materialCode": "YRP00033",
                      "materialName": "冻猪大肠头（预煮）",
                      "weightKg": 100,
                      "utilizationRate": 0.95,
                      "materialCategory": "RAW",
                      "primaryMaterial": true,
                      "remark": "前处理车间配制"
                    }
                  ],
                  "processSteps": [
                    {
                      "sequence": 1,
                      "processName": "蒸煮",
                      "beforeWeightKg": 10,
                      "afterWeightKg": 8,
                      "remainingWeightKg": 1,
                      "remainingDisposition": "REUSE"
                    }
                  ],
                  "finishedOutputWeightKg": 8.5,
                  "finishedOutputQuantity": 17,
                  "finishedOutputUnit": "袋"
                }
                """;

        return mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, taskPrincipal(taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.materials", hasSize(1)))
                .andExpect(jsonPath("$.data.materials[0].materialName").value("冻猪大肠头（预煮）"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
    }

    private String saveExperimentDraftWithPrincipal(String taskId, SessionPrincipal principal) throws Exception {
        return mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorName":"伪造用户","materials":[{"stage":"原料","sequence":1,"materialCode":"RAW-001","materialName":"主料","weightKg":10,"materialCategory":"RAW","primaryMaterial":true}],
                                 "processSteps":[{"sequence":1,"processName":"熟制","beforeWeightKg":10,"afterWeightKg":8}],
                                 "finishedOutputWeightKg":8,"finishedOutputQuantity":8,"finishedOutputUnit":"袋"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String submitFormalProcessRevision(String formId, SessionPrincipal principal) {
        var current = processPlanService.find(formId);
        var step = new ProcessPlan.MinorStep(null, 1, "COOK", "熟制", "NORMAL", null, null, null, null, null,
                null, null, null, List.of(new ProcessPlan.StepMaterial(null, 1, "PRIMARY", "YRP00033", "主料", "SOLID",
                new BigDecimal("10"), "YRP00033", null, "EXTERNAL", null)), List.of(
                new ProcessPlan.StepOutput("OUT-TEST-HANDOFF-" + formId, 1, "FINISHED", "成品", "SOLID", new BigDecimal("8"), true, false, null)), List.of());
        var major = new ProcessPlan.MajorProcess(null, 1, "COOK", "熟制", null, "PRIMARY_INPUT", "熟制损耗已说明",
                List.of(step), List.of(), List.of(), null);
        var saved = processPlanService.save(formId, new ProcessPlan(null, formId, current.versionNo(), "DRAFT", List.of(major), null, false), principal);
        return processRevisionService.submit(formId,
                new com.lhr.rnd.service.ProcessRevisionService.SubmitCommand(saved.versionNo(), true, "首次正式提交", principal.name()), principal).id();
    }

    private SessionPrincipal ownerPrincipal() {
        return new SessionPrincipal("TEST-ASSIGNEE", "test_assignee", "张研发", null, "RND_ENGINEER", Instant.now().plusSeconds(60));
    }

    private SessionPrincipal testerPrincipal() {
        return new SessionPrincipal("TEST-TESTER", "test_tester", "内部测试员", "ou-test-tester", "TESTER", Instant.now().plusSeconds(60));
    }

    private Object afterGate(CountDownLatch gate, java.util.function.Supplier<?> action) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) return new IllegalStateException("并发测试启动超时");
            return action.get();
        } catch (Throwable error) {
            var cause = error;
            while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
            return cause;
        }
    }

    private String saveSubmissionValidationDraft(
            String taskId,
            String primaryWeightKg,
            String processSteps,
            String finishedOutputWeightKg,
            String finishedOutputQuantity) throws Exception {
        var draftJson = """
                {
                  "operatorName": "张研发",
                  "materials": [
                    {
                      "stage": "原料",
                      "sequence": 1,
                      "materialName": "主料",
                      "weightKg": %s,
                      "materialCategory": "RAW",
                      "primaryMaterial": true
                    }
                  ],
                  "processSteps": %s,
                  "finishedOutputWeightKg": %s,
                  "finishedOutputQuantity": %s,
                  "finishedOutputUnit": "袋"
                }
                """.formatted(primaryWeightKg, processSteps, finishedOutputWeightKg, finishedOutputQuantity);

        return mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
    }

    private String validProcessSteps() {
        return """
                [{
                  "sequence": 1,
                  "processName": "蒸煮",
                  "beforeWeightKg": 10,
                  "afterWeightKg": 8,
                  "remainingWeightKg": 1,
                  "remainingDisposition": "REUSE"
                }]
                """;
    }

    private void submitExperimentForTestExpecting(String experimentFormId, String expectedCode) throws Exception {
        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, ownerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private String submitExperimentForTest(String experimentFormId) throws Exception {
        var principal = formPrincipal(experimentFormId);
        submitFormalProcessRevision(experimentFormId, principal);
        return mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentForm.status").value("SUBMITTED_FOR_TEST"))
                .andExpect(jsonPath("$.data.testAssignment.testerName").value("内部测试员"))
                .andExpect(jsonPath("$.data.testAssignment.status").value("PENDING_TEST"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"testAssignment\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];
    }

    private SessionPrincipal taskPrincipal(String taskId) {
        return jdbcTemplate.queryForObject("select assignee_user_id, assignee_name from rnd_task where id = ?",
                (rs, row) -> new SessionPrincipal(rs.getString(1), rs.getString(2), rs.getString(2), null,
                        "RND_ENGINEER", Instant.now().plusSeconds(60)), taskId);
    }

    private SessionPrincipal formPrincipal(String formId) {
        return jdbcTemplate.queryForObject("select task.assignee_user_id, task.assignee_name from experiment_form form join rnd_task task on task.id = form.task_id where form.id = ?",
                (rs, row) -> new SessionPrincipal(rs.getString(1), rs.getString(2), rs.getString(2), null,
                        "RND_ENGINEER", Instant.now().plusSeconds(60)), formId);
    }

    private String createShipment(String versionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sample-versions/{id}/shipments", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 6,
                                  "receiverName": "销售内勤",
                                  "trackingNo": "SF202606180099",
                                  "remark": "寄客户确认复热效果"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
    }

    private String generatePricingFile(String versionId) throws Exception {
        var productOwner = valueById("rnd_task", versionId.replaceFirst("^VER", "TASK"), "product_owner_name");
        var owner = principal(productOwner, "RND_ENGINEER");
        var draftResponse = mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT_PACKAGING"))
                .andReturn()
                .getResponse().getContentAsString();
        var pricingFileId = objectMapper.readTree(draftResponse).path("data").path("id").asText();
        var packagingItems = mockMvc.perform(get("/api/v1/pricing-files/{id}/packaging-items", pricingFileId)
                        .requestAttr("sessionPrincipal", owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(packagingItems).path("data");
        mockMvc.perform(put("/api/v1/pricing-files/{id}/packaging-items", pricingFileId)
                        .requestAttr("sessionPrincipal", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PRICING_REVIEW"));
        return pricingFileId;
    }

    private void approvePricingFile(String pricingFileId) throws Exception {
        mockMvc.perform(post("/api/v1/pricing-files/{id}/review", pricingFileId)
                        .requestAttr("sessionPrincipal", principal("张研发", "RND_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reviewerName\":\"张研发\",\"comment\":\"核价无误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINANCE_NOTIFIED"));
    }

    @Test
    void returnsBusinessErrorWhenApprovingMissingRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", "REQ-404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_REQUEST_NOT_FOUND"));
    }

    @Test
    void returnsBusinessErrorWhenAssigningMissingTask() throws Exception {
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", "TASK-404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RND_TASK_NOT_FOUND"));
    }

    private String createApprovedRequest() throws Exception {
        var requestId = createPendingSampleRequest();

        return mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"研发总监\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status").value("PENDING_ASSIGNMENT"))
                .andExpect(jsonPath("$.data.version.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.task.status").value("PENDING_ASSIGNMENT"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"task\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];
    }

    private String createPendingSampleRequest() throws Exception {
        var createRequestJson = """
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

        var requestId = mockMvc.perform(post("/api/v1/sample-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.applicationScenario").value("商超零售冷冻即热，家庭复热即食"))
                .andExpect(jsonPath("$.data.flavorRequirement").value("香卤风味，微辣，复热后卤香明显"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
        return requestId;
    }

    private void assignTask(String taskId) throws Exception {
        assignTask(taskId, "张研发");
    }

    private void assignTask(String taskId, String assigneeName) throws Exception {
        var accountId = "ASSIGN-" + assigneeName.hashCode();
        if (!"张研发".equals(assigneeName) && jdbcTemplate.queryForObject("select count(*) from user_account where name = ? and status = 'ACTIVE'", Integer.class, assigneeName) == 0) jdbcTemplate.update("insert into user_account(id,username,password_hash,name,feishu_user_id,role,status,created_at,updated_at) values (?,?,?,?,?,?,?,current_timestamp,current_timestamp)",
                accountId, "assign_" + Math.abs(assigneeName.hashCode()), "x", assigneeName, "ou-" + Math.abs(assigneeName.hashCode()), "RND_ENGINEER", "ACTIVE");
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"%s\",\"dueDate\":\"2026-06-25\"}".formatted(assigneeName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"));
    }

    private SessionPrincipal principal(String name, String role) {
        return new SessionPrincipal("USER-" + name, name, name, null, role, Instant.parse("2026-07-12T00:00:00Z"));
    }

    private void bindFeishuUser(String name, String feishuUserId) throws Exception {
        mockMvc.perform(post("/api/v1/feishu/users/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "feishuUserId": "%s",
                                  "role": "RND_ENGINEER",
                                  "departmentName": "研发部"
                                }
                                """.formatted(name, feishuUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feishuUserId").value(feishuUserId));
    }

    private void disableWorkflowAction(String currentStatus, String actionCode, String nextStatus) {
        jdbcTemplate.update(
                """
                        insert into workflow_rule_config (
                            id, workflow_code, current_status, action_code, action_label, next_status,
                            enabled, notify_feishu, notify_role, sort_order, remark, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "FLOW-DIS-" + Math.abs((currentStatus + actionCode).hashCode()),
                "SAMPLE_RND_FLOW",
                currentStatus,
                actionCode,
                "禁用动作",
                nextStatus,
                false,
                false,
                null,
                10,
                "测试禁用流程动作"
        );
    }

    private int countById(String tableName, String id) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName + " where id = ?", Integer.class, id);
    }

    private int countBySampleNo(String tableName, String sampleNo) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName + " where sample_no = ?", Integer.class, sampleNo);
    }

    private int countByColumn(String tableName, String columnName, String value) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + columnName + " = ?", Integer.class, value);
    }

    private String valueById(String tableName, String id, String columnName) {
        return jdbcTemplate.queryForObject("select " + columnName + " from " + tableName + " where id = ?", String.class, id);
    }

    private String valueByColumn(String tableName, String lookupColumnName, String value, String columnName) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where " + lookupColumnName + " = ?",
                String.class,
                value
        );
    }

    private String valueByColumns(
            String tableName,
            String firstColumnName,
            String firstValue,
            String secondColumnName,
            String secondValue,
            String resultColumnName
    ) {
        return jdbcTemplate.queryForObject(
                "select " + resultColumnName + " from " + tableName
                        + " where " + firstColumnName + " = ? and " + secondColumnName + " = ?",
                String.class,
                firstValue,
                secondValue
        );
    }

    private String createLockedSampleVersion() throws Exception {
        return createLockedSampleVersion("张研发");
    }

    private String createLockedSampleVersion(String productOwnerName) throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId, productOwnerName);
        acceptTask(taskId, productOwnerName);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);
        return mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
                        .requestAttr(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE, testerPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"口味和复热状态通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentForm.status").value("LOCKED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"experimentForm\\\":\\{")[1]
                .split("\\\"versionId\\\":\\\"")[1]
                .split("\"")[0];
    }

    private String confirmTogether(String pricingFileId, List<com.lhr.rnd.model.PricingPackagingItem> items,
                                   CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await();
            workflowService.confirmPricingPackaging(pricingFileId, items, "张研发", "RND_ENGINEER");
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.code();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void insertRevisionTogether(String formId, String revisionId, int revisionNo,
                                        String outputWeightKg, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await();
            insertFormalPricingRevision(formId, revisionId, revisionNo, outputWeightKg);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String createPricingDraft(String versionId) throws Exception {
        var draft = mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT_PACKAGING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(draft).path("data").path("id").asText();
    }

    private List<com.lhr.rnd.model.PricingPackagingItem> modifiedQuantity(
            List<com.lhr.rnd.model.PricingPackagingItem> items) {
        var result = new java.util.ArrayList<>(items);
        var first = result.get(0);
        result.set(0, new com.lhr.rnd.model.PricingPackagingItem(
                first.id(), first.pricingFileId(), first.sequence(), first.source(), first.materialCode(),
                first.materialName(), first.quantity().add(BigDecimal.ONE), first.packageSpec(),
                first.conversionRule(), first.remark(), first.confirmationStatus(), first.modificationReason()));
        return List.copyOf(result);
    }

    private List<Map<String, Object>> pricingPackagingRows(String pricingFileId) {
        return jdbcTemplate.queryForList(
                "select sequence,source,material_code,material_name,quantity,package_spec,confirmation_status "
                        + "from pricing_packaging_item where pricing_file_id = ? order by sequence", pricingFileId);
    }

    private Map<String, String> pricingArchiveFiles(String versionId) throws Exception {
        var sampleNo = valueById("sample_version", versionId, "sample_no");
        var versionCode = valueById("sample_version", versionId, "version_code");
        var directory = Path.of("target", "rnd-archive", "pricing-archives", sampleNo, versionCode, "pricing");
        if (!Files.exists(directory)) return Map.of();
        var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                result.put(path.getFileName().toString(), sha256Bytes(Files.readAllBytes(path)));
            }
        }
        return Map.copyOf(result);
    }

    private Path pricingArchivePath(String storageKey) {
        return Path.of("target", "rnd-archive").resolve(storageKey);
    }

    @SuppressWarnings("unchecked")
    private PricingFileRecord cachedPricingFile(String pricingFileId) {
        return ((Map<String, PricingFileRecord>) ReflectionTestUtils.getField(workflowService, "pricingFiles"))
                .get(pricingFileId);
    }

    private void assertPricingPersistenceCoherent(
            String pricingFileId,
            List<com.lhr.rnd.model.PricingPackagingItem> items,
            String revisionId,
            String finishedYield
    ) throws Exception {
        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("PENDING_PRICING_REVIEW");
        assertThat(valueById("pricing_file", pricingFileId, "process_revision_id")).isEqualTo(revisionId);
        assertThat(cachedPricingFile(pricingFileId).processRevisionId()).isEqualTo(revisionId);
        assertThat(cachedPricingFile(pricingFileId).status().name()).isEqualTo("PENDING_PRICING_REVIEW");
        assertThat(pricingPackagingRows(pricingFileId)).hasSize(items.size());
        var persistedQuantities = jdbcTemplate.query(
                "select quantity from pricing_packaging_item where pricing_file_id = ? order by sequence",
                (resultSet, row) -> resultSet.getBigDecimal(1), pricingFileId);
        assertThat(persistedQuantities).containsExactlyElementsOf(
                items.stream().map(com.lhr.rnd.model.PricingPackagingItem::quantity).toList());
        assertThat(countById("experiment_process_revision", revisionId)).isEqualTo(1);
        assertThat(countByColumn("archive_file", "business_id", pricingFileId)).isEqualTo(1);
        var archive = jdbcTemplate.queryForMap("select * from archive_file where business_id = ?", pricingFileId);
        assertThat(archive.get("remark")).isEqualTo("processRevisionId=" + revisionId);
        assertThat(((Number) archive.get("file_size")).longValue())
                .isEqualTo(Long.parseLong(valueById("pricing_file", pricingFileId, "content_length")));
        assertThat(archive.get("file_name")).isEqualTo(valueById("pricing_file", pricingFileId, "file_name"));
        var archiveBytes = workflowService.downloadArchiveFile((String) archive.get("id")).content();
        var pricingBytes = workflowService.downloadPricingFile(pricingFileId).content();
        assertThat(archiveBytes).isEqualTo(pricingBytes);
        assertPricingWorkbookRevision(pricingBytes, revisionId, finishedYield);
    }

    private void assertPricingWorkbookRevision(byte[] bytes, String revisionId, String finishedYield) throws Exception {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(8).getCell(6).getStringCellValue())
                    .contains("revisionId=" + revisionId,
                            "finishedYield=" + new BigDecimal(finishedYield).setScale(6) + "%");
            assertThat(sheet.getRow(15).getCell(6).getNumericCellValue())
                    .isEqualTo(new BigDecimal(finishedYield).movePointLeft(2).doubleValue());
        }
    }

    private void insertFormalPricingRevision(String formId, String revisionId, int revisionNo, String outputWeightKg) throws Exception {
        insertFormalPricingRevision(formId, revisionId, revisionNo, outputWeightKg, "PRIMARY_INPUT");
    }

    private void insertFormalPricingRevision(String formId, String revisionId, int revisionNo,
                                             String outputWeightKg, String yieldBasis) throws Exception {
        var storedMaxRevision = jdbcTemplate.queryForObject("select coalesce(max(revision_no), 0) from experiment_process_revision where experiment_form_id = ?", Integer.class, formId);
        var actualRevisionNo = Math.max(revisionNo, storedMaxRevision + 1);
        var planId = "PLAN-" + revisionId;
        var snapshot = new ProcessPlan(
                planId, formId, 1, "SUBMITTED",
                java.util.List.of(new ProcessPlan.MajorProcess(
                        "MAJOR-" + revisionId, 1, "COOK", "熟制", null, yieldBasis, null,
                        java.util.List.of(new ProcessPlan.MinorStep(
                                "STEP-" + revisionId, 1, "COOK", "熟制", "NORMAL", null, null, null,
                                null, null, null, null, null,
                                java.util.List.of(new ProcessPlan.StepMaterial(
                                        "MATERIAL-" + revisionId, 1, "PRIMARY", "YRP00033", "正式主料名称", "SOLID",
                                        new BigDecimal("100"), "YRP00033", null, "EXTERNAL", null)),
                                outputWeightKg == null ? java.util.List.of() : java.util.List.of(new ProcessPlan.StepOutput(
                                        "OUTPUT-" + revisionId, 1, "FINISHED", "正式成品", "SOLID",
                                        new BigDecimal(outputWeightKg), true, false, null)),
                                java.util.List.of())),
                        java.util.List.of(), java.util.List.of(), null)),
                null, false);
        var snapshotJson = objectMapper.writeValueAsString(snapshot);
        if (jdbcTemplate.queryForObject("select count(*) from experiment_process_plan where experiment_form_id = ?", Integer.class, formId) == 0) {
            jdbcTemplate.update("insert into experiment_process_plan(id,experiment_form_id,version_no,status,calculation_mode,balance_tolerance_kg,created_at,updated_at) values (?,?,?,?,?,?,?,?)",
                    planId, formId, 1, "SUBMITTED", "PRIMARY_INPUT", new BigDecimal("0.0100"), LocalDateTime.now(), LocalDateTime.now());
        } else {
            planId = jdbcTemplate.queryForObject("select id from experiment_process_plan where experiment_form_id = ?", String.class, formId);
            snapshot = new ProcessPlan(planId, snapshot.experimentFormId(), snapshot.versionNo(), snapshot.status(), snapshot.majorProcesses(),
                    snapshot.batchYieldPercent(), snapshot.balanceToleranceKg(), snapshot.legacy(), snapshot.sourceRevisionId(), snapshot.changeReason());
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        }
        jdbcTemplate.update("insert into experiment_process_revision(id,process_plan_id,experiment_form_id,revision_no,submitted_by,submitted_at,snapshot_json,snapshot_hash) values (?,?,?,?,?,?,?,?)",
                revisionId, planId, formId, actualRevisionNo, "张研发", LocalDateTime.now(), snapshotJson, sha256(snapshotJson));
    }

    private String sha256(String value) throws Exception {
        var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        var result = new StringBuilder(bytes.length * 2);
        for (var item : bytes) result.append(String.format("%02x", item));
        return result.toString();
    }

    private String sha256Bytes(byte[] value) throws Exception {
        var bytes = MessageDigest.getInstance("SHA-256").digest(value);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
