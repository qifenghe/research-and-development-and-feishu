package com.lhr.rnd.api;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.lhr.rnd.service.SampleWorkflowService workflowService;

    @BeforeEach
    void clearWorkflowState() {
        for (String table : new String[]{
                "audit_log",
                "archive_file",
                "finance_notification",
                "pricing_file",
                "customer_feedback",
                "shipment_record",
                "test_record",
                "test_assignment",
                "experiment_process",
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
    }

    @Test
    void exportsExperimentFormWorkbookAndArchivesIt() throws Exception {
        var experimentFormId = createExperimentForm();

        var response = mockMvc.perform(get("/api/v1/reports/experiment-forms/{id}/export", experimentFormId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment; filename*=UTF-8''")))
                .andReturn()
                .getResponse();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("打样实验单");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("500g香卤大肠头");
            assertThat(sheet.getRow(6).getCell(3).getStringCellValue()).isEqualTo("76%");
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("原料");
            assertThat(sheet.getRow(9).getCell(1).getStringCellValue()).isEqualTo("是");
            assertThat(sheet.getRow(9).getCell(4).getStringCellValue()).isEqualTo("冻猪大肠头（预煮）");
            assertThat(sheet.getRow(9).getCell(5).getStringCellValue()).isEqualTo("90.91%");
            assertThat(sheet.getRow(15).getCell(1).getStringCellValue()).contains("清洗");
            assertThat(sheet.getRow(15).getCell(4).getStringCellValue()).isEqualTo("1.0000");
            assertThat(sheet.getRow(15).getCell(5).getStringCellValue()).isEqualTo("退回");
            assertThat(sheet.getRow(15).getCell(6).getStringCellValue()).isEqualTo("3.0000");
            assertThat(sheet.getRow(15).getCell(7).getStringCellValue()).isEqualTo("3%");
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from archive_file where business_type = 'REPORT_EXPERIMENT_FORM' and business_id = ?",
                Integer.class,
                experimentFormId
        )).isEqualTo(1);
    }

    @Test
    void exportsRndTaskListWorkbook() throws Exception {
        createExperimentForm();

        var response = mockMvc.perform(get("/api/v1/reports/rnd-tasks/export")
                        .param("keyword", "大肠头"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("打样任务列表");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("500g香卤大肠头");
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("张研发");
        }
    }

    private String createExperimentForm() throws Exception {
        var requestId = mockMvc.perform(post("/api/v1/sample-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "500g香卤大肠头",
                                  "productType": "冷冻即热菜",
                                  "customerName": "LHYC",
                                  "specification": "500g/袋",
                                  "applicationScenario": "商超零售冷冻即热，家庭复热即食",
                                  "flavorRequirement": "香卤风味，微辣，复热后卤香明显",
                                  "creatorName": "赵内勤"
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        var taskId = mockMvc.perform(post("/api/v1/sample-requests/{id}/approve", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"赵总监\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\\\"task\\\":\\{\\\"id\\\":\\\"")[1]
                .split("\"")[0];

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeName\":\"张研发\",\"dueDate\":\"2026-06-25\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"张研发\"}"))
                .andExpect(status().isOk());

        return mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorName": "张研发",
                                  "summary": "A0 香卤口味第一版，复热后卤香明显",
                                  "materials": [
                                    {
                                      "stage": "原料",
                                      "sequence": 1,
                                      "materialCode": "YRP00033",
                                      "materialName": "冻猪大肠头（预煮）",
                                      "weightKg": 100,
                                      "materialCategory": "RAW",
                                      "primaryMaterial": true,
                                      "utilizationRate": 0.95,
                                      "remark": "前处理车间配制"
                                    },
                                    {
                                      "stage": "辅料",
                                      "sequence": 2,
                                      "materialCode": "FL00001",
                                      "materialName": "香卤料包",
                                      "weightKg": 10,
                                      "materialCategory": "AUXILIARY",
                                      "primaryMaterial": false,
                                      "utilizationRate": 1
                                    }
                                  ],
                                  "processSteps": [
                                    {
                                      "sequence": 1,
                                      "processName": "清洗",
                                      "beforeWeightKg": 100,
                                      "afterWeightKg": 96,
                                      "remainingWeightKg": 1,
                                      "remainingDisposition": "RETURN",
                                      "remark": "流水冲洗并修整，去除多余油脂"
                                    }
                                  ],
                                  "finishedOutputWeightKg": 80
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];
    }
}
