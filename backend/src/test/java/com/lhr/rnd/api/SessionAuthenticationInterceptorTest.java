package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.SessionProperties;
import com.lhr.rnd.service.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

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
    void permitsDirectorToLoadTheShipmentListRequiredByTheH5TodoBoard() throws Exception {
        var directorToken = tokenFor("待办总监", "ou_todo_director", "RND_DIRECTOR");

        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());
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
