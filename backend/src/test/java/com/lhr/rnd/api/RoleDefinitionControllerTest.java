package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rnd.session.auth-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void superAdminCanCreateRoleAndAssignedRoleCannotBeDisabled() throws Exception {
        var adminToken = tokenFor("角色管理员", "ou_role_admin", "SYSTEM_ADMIN");

        mockMvc.perform(post("/api/v1/settings/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"RND_VIEWER","roleName":"研发查看员","description":"仅查看研发资料"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("RND_VIEWER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/settings/users/new-viewer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rnd_viewer","name":"研发查看员甲","role":"RND_VIEWER","departmentName":"研发部","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("RND_VIEWER"));

        mockMvc.perform(post("/api/v1/settings/roles/RND_VIEWER/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
    }

    @Test
    void accountCannotUseUnknownRole() throws Exception {
        var adminToken = tokenFor("账号管理员", "ou_role_account_admin", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/users/new-unknown-role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown_role","name":"未知角色人员","role":"MISSING_ROLE","departmentName":"研发部","password":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
    }

    private String tokenFor(String name, String feishuUserId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/feishu/users/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","feishuUserId":"%s","role":"%s","departmentName":"信息部"}
                                """.formatted(name, feishuUserId, role)))
                .andExpect(status().isOk());
        var response = mockMvc.perform(post("/api/v1/feishu/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock:%s\"}".formatted(feishuUserId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
