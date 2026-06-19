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
class RolePermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanSaveAndReadRolePermissions() throws Exception {
        var adminToken = tokenFor("系统管理员", "ou_admin_perm_001", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/role-permissions/{roleCode}", "CONFIG_TEST_ROLE")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissions": [
                                    {
                                      "httpMethod": "POST",
                                      "pathPattern": "/api/v1/sample-requests",
                                      "enabled": true,
                                      "description": "允许研发人员临时录入样品需求",
                                      "sortOrder": 10
                                    },
                                    {
                                      "httpMethod": "POST",
                                      "pathPattern": "/api/v1/sample-requests/*/approve",
                                      "enabled": false,
                                      "description": "研发人员不可审核需求",
                                      "sortOrder": 20
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("CONFIG_TEST_ROLE"))
                .andExpect(jsonPath("$.data.permissions.length()").value(2))
                .andExpect(jsonPath("$.data.permissions[0].pathPattern").value("/api/v1/sample-requests"));

        mockMvc.perform(get("/api/v1/settings/role-permissions/{roleCode}", "CONFIG_TEST_ROLE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("CONFIG_TEST_ROLE"))
                .andExpect(jsonPath("$.data.permissions.length()").value(2))
                .andExpect(jsonPath("$.data.permissions[0].enabled").value(true))
                .andExpect(jsonPath("$.data.permissions[1].enabled").value(false));
    }

    @Test
    void nonAdminCannotSaveRolePermissions() throws Exception {
        var engineerToken = tokenFor("研发人员权限测试", "ou_engineer_perm_001", "RND_ENGINEER");

        mockMvc.perform(put("/api/v1/settings/role-permissions/{roleCode}", "RND_ENGINEER")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ROLE_FORBIDDEN"));
    }

    @Test
    void adminCanInitializeDefaultRolePermissionsWithoutOverwritingConfiguredRoles() throws Exception {
        var adminToken = tokenFor("默认权限管理员", "ou_admin_perm_002", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/role-permissions/{roleCode}", "CONFIG_KEEP_ROLE")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissions": [
                                    {
                                      "httpMethod": "GET",
                                      "pathPattern": "/api/v1/sample-requests",
                                      "enabled": true,
                                      "description": "保留已有自定义权限",
                                      "sortOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/settings/role-permissions/defaults/initialize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[?(@.roleCode == 'RND_ASSISTANT')]").exists())
                .andExpect(jsonPath("$.data[?(@.roleCode == 'RND_DIRECTOR')]").exists())
                .andExpect(jsonPath("$.data[?(@.roleCode == 'RND_ENGINEER')]").exists());

        mockMvc.perform(get("/api/v1/settings/role-permissions/{roleCode}", "RND_ASSISTANT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0].pathPattern").value("/api/v1/sample-requests"));

        mockMvc.perform(get("/api/v1/settings/role-permissions/{roleCode}", "CONFIG_KEEP_ROLE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(1))
                .andExpect(jsonPath("$.data.permissions[0].description").value("保留已有自定义权限"));
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
