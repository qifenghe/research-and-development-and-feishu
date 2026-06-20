package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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

    @BeforeEach
    void clearWorkflowState() {
        clearBusinessTables();
        clearWorkflowServiceMemory();
    }

    private void clearBusinessTables() {
        for (String table : new String[]{
                "audit_log",
                "archive_file",
                "finance_notification",
                "pricing_file",
                "customer_feedback",
                "shipment_record",
                "test_record",
                "test_assignment",
                "experiment_material",
                "experiment_form",
                "feishu_notification",
                "user_account",
                "rnd_task",
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
    void submittingExperimentUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        disableWorkflowAction("SAMPLING", "SUBMIT_EXPERIMENT", "PENDING_TEST");

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\",\"comment\":\"需要调整咸度后复打样\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_STATUS_TRANSITION_ILLEGAL"));
    }

    @Test
    void bindingFeishuUserCreatesTaskAssignmentNotificationWhenTaskIsAssigned() throws Exception {
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

        acceptTask(taskId);
        assertThat(valueById("rnd_task", taskId, "status")).isEqualTo("SAMPLING");
        assertThat(valueById("rnd_task", taskId, "accepted_at")).isNotBlank();
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

        var pricingFileId = mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(versionId))
                .andExpect(jsonPath("$.data.pricingVersion").value("A0-核价V1"))
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.fileName").value("500g香卤大肠头-核价原料清单-A0-V1.xlsx"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        assertThat(countById("pricing_file", pricingFileId)).isEqualTo(1);
        assertThat(valueById("pricing_file", pricingFileId, "version_id")).isEqualTo(versionId);
        assertThat(valueById("pricing_file", pricingFileId, "pricing_version")).isEqualTo("A0-核价V1");
        assertThat(valueById("pricing_file", pricingFileId, "status")).isEqualTo("GENERATED");
        assertThat(valueById("pricing_file", pricingFileId, "file_name")).isEqualTo("500g香卤大肠头-核价原料清单-A0-V1.xlsx");
        assertThat(countByColumn("archive_file", "business_id", pricingFileId)).isEqualTo(1);
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "business_type")).isEqualTo("PRICING_FILE");
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "version_id")).isEqualTo(versionId);
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_name"))
                .isEqualTo("500g香卤大肠头-核价原料清单-A0-V1.xlsx");
        var archivedSampleNo = valueById("pricing_file", pricingFileId, "sample_no");
        assertThat(valueByColumn("archive_file", "business_id", pricingFileId, "file_path"))
                .isEqualTo(archivedSampleNo + "/A0/核价/500g香卤大肠头-核价原料清单-A0-V1.xlsx");
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
                .andExpect(jsonPath("$.data[0].fileName").value("500g香卤大肠头-核价原料清单-A0-V1.xlsx"))
                .andExpect(jsonPath("$.data[0].fileStatus").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/archive-files/{id}/download", archiveFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(archivedPath)));

        var financeNotificationId = mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
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
    void notifyingFinanceUsesWorkflowConfigAndRejectsDisabledAction() throws Exception {
        var versionId = createLockedSampleVersion();
        var pricingFileId = generatePricingFile(versionId);
        disableWorkflowAction("PRICING_FILE_GENERATED", "NOTIFY_FINANCE", "FINANCE_NOTIFIED");

        mockMvc.perform(post("/api/v1/pricing-files/{id}/notify-finance", pricingFileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"财务核价员\",\"remark\":\"请核价\"}"))
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
                        .param("status", "GENERATED")
                        .param("keyword", "香卤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pricingFileId))
                .andExpect(jsonPath("$.data[0].status").value("GENERATED"));
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
                        .param("status", "GENERATED")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(secondPricingId));

        mockMvc.perform(get("/api/v1/pricing-files")
                        .param("status", "GENERATED")
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
                .andExpect(jsonPath("$.data.fieldGroups[1].title").value("任务信息"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("ASSIGN_TASK"))
                .andExpect(jsonPath("$.data.availableActions[0].endpoint").value("/api/v1/rnd-tasks/" + taskId + "/assign"));

        assignTask(taskId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(1)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("ACCEPT_TASK"));

        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "RND_ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SAMPLING"))
                .andExpect(jsonPath("$.data.currentExperimentForm.id").value(experimentFormId))
                .andExpect(jsonPath("$.data.fieldGroups[2].title").value("实验单"))
                .andExpect(jsonPath("$.data.availableActions", hasSize(2)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("SAVE_EXPERIMENT_DRAFT"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("SUBMIT_EXPERIMENT_TEST"));

        var testAssignmentId = submitExperimentForTest(experimentFormId);

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "TESTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PENDING_TEST"))
                .andExpect(jsonPath("$.data.currentTestAssignment.id").value(testAssignmentId))
                .andExpect(jsonPath("$.data.availableActions", hasSize(2)))
                .andExpect(jsonPath("$.data.availableActions[0].code").value("PASS_INTERNAL_TEST"))
                .andExpect(jsonPath("$.data.availableActions[1].code").value("FAIL_RESAMPLE"));

        mockMvc.perform(get("/api/v1/rnd-tasks/{id}/detail", taskId)
                        .param("role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableActions", hasSize(0)));
    }

    private void acceptTask(String taskId) throws Exception {
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"张研发\"}"))
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
                      "remark": "前处理车间配制"
                    }
                  ]
                }
                """;

        return mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
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

    private String submitExperimentForTest(String experimentFormId) throws Exception {
        return mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", experimentFormId)
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
        return mockMvc.perform(post("/api/v1/sample-versions/{id}/pricing-files", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
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
                  "creatorName": "研发内勤"
                }
                """;

        var requestId = mockMvc.perform(post("/api/v1/sample-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
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
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"%s\",\"dueDate\":\"2026-06-25\"}".formatted(assigneeName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"));
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
        var taskId = createApprovedRequest();
        assignTask(taskId);
        acceptTask(taskId);
        var experimentFormId = saveExperimentDraft(taskId);
        var testAssignmentId = submitExperimentForTest(experimentFormId);
        return mockMvc.perform(post("/api/v1/test-assignments/{id}/pass", testAssignmentId)
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
}
