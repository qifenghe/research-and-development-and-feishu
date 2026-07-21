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
class UserSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCreateUpdateDisableEnableAndListUsers() throws Exception {
        var adminToken = tokenFor("人员管理员", "ou_user_admin_001", "SYSTEM_ADMIN");

        var userId = mockMvc.perform(put("/api/v1/settings/users/{feishuUserId}", "ou_rnd_user_001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "研发人员配置A",
                                  "role": "RND_ENGINEER",
                                  "departmentName": "研发一组"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("研发人员配置A"))
                .andExpect(jsonPath("$.data.role").value("RND_ENGINEER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        mockMvc.perform(put("/api/v1/settings/users/{feishuUserId}", "ou_rnd_user_001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "研发总监配置A",
                                  "role": "RND_DIRECTOR",
                                  "departmentName": "研发中心"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.name").value("研发总监配置A"))
                .andExpect(jsonPath("$.data.role").value("RND_DIRECTOR"));

        mockMvc.perform(post("/api/v1/settings/users/{id}/disable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(put("/api/v1/settings/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "rnd_user_config_a",
                                  "name": "研发人员配置A（已停用）",
                                  "role": "RND_ENGINEER",
                                  "departmentName": "研发一组"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(post("/api/v1/settings/users/{id}/enable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.feishuUserId == 'ou_rnd_user_001')]").exists());
    }

    @Test
    void nonAdminCannotManageUsers() throws Exception {
        var engineerToken = tokenFor("非管理员人员配置", "ou_user_engineer_001", "RND_ENGINEER");

        mockMvc.perform(put("/api/v1/settings/users/{feishuUserId}", "ou_rnd_user_002")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "研发人员配置B",
                                  "role": "RND_ENGINEER",
                                  "departmentName": "研发二组"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
    }

    @Test
    void cannotDisableOrReassignTheLastActiveSuperAdmin() throws Exception {
        var adminToken = tokenFor("唯一超级管理员", "ou_only_super_admin", "SYSTEM_ADMIN");
        var adminId = userIdFor("ou_only_super_admin", adminToken);
        disableOtherSuperAdmins(adminId, adminToken);

        mockMvc.perform(post("/api/v1/settings/users/{id}/disable", adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_SYSTEM_ADMIN_REQUIRED"));

        mockMvc.perform(put("/api/v1/settings/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "only_super_admin",
                                  "name": "唯一超级管理员",
                                  "role": "RND_DIRECTOR",
                                  "departmentName": "信息部"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_SYSTEM_ADMIN_REQUIRED"));
    }

    private String userIdFor(String feishuUserId, String adminToken) throws Exception {
        var response = mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var users = objectMapper.readTree(response).path("data");
        for (var user : users) {
            if (feishuUserId.equals(user.path("feishuUserId").asText())) {
                return user.path("id").asText();
            }
        }
        throw new IllegalStateException("未找到超级管理员账号");
    }

    private void disableOtherSuperAdmins(String protectedUserId, String adminToken) throws Exception {
        var response = mockMvc.perform(get("/api/v1/settings/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (var user : objectMapper.readTree(response).path("data")) {
            if ("SYSTEM_ADMIN".equals(user.path("role").asText())
                    && "ACTIVE".equals(user.path("status").asText())
                    && !protectedUserId.equals(user.path("id").asText())) {
                mockMvc.perform(post("/api/v1/settings/users/{id}/disable", user.path("id").asText())
                                .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk());
            }
        }
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
