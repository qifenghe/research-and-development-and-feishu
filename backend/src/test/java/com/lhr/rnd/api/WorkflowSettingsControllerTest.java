package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rnd.session.auth-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanSaveReadAndInitializeWorkflowRulesWithoutOverwritingExistingFlow() throws Exception {
        var adminToken = tokenFor("流程管理员", "ou_workflow_admin_001", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/workflows/{workflowCode}", "CONFIG_SAMPLE_FLOW")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {
                                      "currentStatus": "PENDING_REVIEW",
                                      "actionCode": "APPROVE_REQUEST",
                                      "actionLabel": "审核通过",
                                      "nextStatus": "PENDING_ASSIGNMENT",
                                      "enabled": true,
                                      "notifyFeishu": true,
                                      "notifyRole": "RND_DIRECTOR",
                                      "sortOrder": 10,
                                      "remark": "研发总监审核通过后进入待分发"
                                    },
                                    {
                                      "currentStatus": "PENDING_ASSIGNMENT",
                                      "actionCode": "ASSIGN_TASK",
                                      "actionLabel": "分发任务",
                                      "nextStatus": "PENDING_ACCEPTANCE",
                                      "enabled": true,
                                      "notifyFeishu": true,
                                      "notifyRole": "RND_ENGINEER",
                                      "sortOrder": 20
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowCode").value("CONFIG_SAMPLE_FLOW"))
                .andExpect(jsonPath("$.data.rules.length()").value(2))
                .andExpect(jsonPath("$.data.rules[0].actionCode").value("APPROVE_REQUEST"))
                .andExpect(jsonPath("$.data.rules[0].notifyFeishu").value(true));

        mockMvc.perform(get("/api/v1/settings/workflows/{workflowCode}", "CONFIG_SAMPLE_FLOW")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowCode").value("CONFIG_SAMPLE_FLOW"))
                .andExpect(jsonPath("$.data.rules.length()").value(2))
                .andExpect(jsonPath("$.data.rules[0].remark").value("研发总监审核通过后进入待分发"));

        mockMvc.perform(post("/api/v1/settings/workflows/defaults/initialize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.workflowCode == 'SAMPLE_RND_FLOW')]").exists());

        mockMvc.perform(get("/api/v1/settings/workflows/{workflowCode}", "CONFIG_SAMPLE_FLOW")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules.length()").value(2))
                .andExpect(jsonPath("$.data.rules[0].actionLabel").value("审核通过"));
    }

    @Test
    void nonAdminCannotManageWorkflowRules() throws Exception {
        var engineerToken = tokenFor("非管理员流程配置", "ou_workflow_engineer_001", "RND_ENGINEER");

        mockMvc.perform(put("/api/v1/settings/workflows/{workflowCode}", "CONFIG_SAMPLE_FLOW")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
    }

    private String tokenFor(String name, String feishuUserId, String role) throws Exception {
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

        var response = mockMvc.perform(post("/api/v1/feishu/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock:%s\"}".formatted(feishuUserId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
