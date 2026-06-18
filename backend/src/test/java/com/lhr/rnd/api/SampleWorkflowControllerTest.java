package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SampleWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsRequestApprovesToProjectAndAssignsTask() throws Exception {
        createApprovedRequest();

        mockMvc.perform(get("/api/v1/rnd-tasks/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sampleNo").value("YP202606180001"))
                .andExpect(jsonPath("$.data[0].productName").value("500g香卤大肠头"));

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", "TASK-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.assigneeName").value("张研发"));
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

    private void assignTask(String taskId) throws Exception {
        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPTANCE"));
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
