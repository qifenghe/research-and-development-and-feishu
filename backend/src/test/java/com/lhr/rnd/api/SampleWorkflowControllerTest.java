package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void acceptsTaskSavesExperimentDraftAndSubmitsInternalTest() throws Exception {
        var taskId = createApprovedRequest();
        assignTask(taskId);

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/accept", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedBy\":\"张研发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAMPLING"));

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

        mockMvc.perform(post("/api/v1/rnd-tasks/{id}/experiment-form/draft", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("EXP-0001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.versionCode").value("A0"))
                .andExpect(jsonPath("$.data.materials", hasSize(1)))
                .andExpect(jsonPath("$.data.materials[0].materialName").value("冻猪大肠头（预煮）"));

        mockMvc.perform(post("/api/v1/experiment-forms/{id}/submit-test", "EXP-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testerName\":\"内部测试员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentForm.status").value("SUBMITTED_FOR_TEST"))
                .andExpect(jsonPath("$.data.testAssignment.id").value("TEST-0001"))
                .andExpect(jsonPath("$.data.testAssignment.testerName").value("内部测试员"))
                .andExpect(jsonPath("$.data.testAssignment.status").value("PENDING_TEST"));
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
}
