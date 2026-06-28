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
    void allowsFeishuIntegrationStatusWithoutSessionToken() throws Exception {
        mockMvc.perform(get("/api/v1/feishu/integration/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").exists());
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

    private String tokenFor(String name, String feishuUserId, String role) throws Exception {
        bindUser(name, feishuUserId, role);
        return oauthToken(feishuUserId);
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
