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
class DictionarySettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanSaveReadAndInitializeDictionariesWithoutOverwritingExistingCategory() throws Exception {
        var adminToken = tokenFor("字典管理员", "ou_dictionary_admin_001", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/dictionaries/{category}", "CONFIG_PRODUCT_TYPE")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "itemCode": "FROZEN_READY_MEAL",
                                      "itemLabel": "冷冻即热菜",
                                      "enabled": true,
                                      "sortOrder": 10,
                                      "remark": "研发样品主分类"
                                    },
                                    {
                                      "itemCode": "CURED_PRODUCT",
                                      "itemLabel": "腊制品",
                                      "enabled": true,
                                      "sortOrder": 20
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("CONFIG_PRODUCT_TYPE"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("FROZEN_READY_MEAL"))
                .andExpect(jsonPath("$.data.items[0].itemLabel").value("冷冻即热菜"));

        mockMvc.perform(get("/api/v1/settings/dictionaries/{category}", "CONFIG_PRODUCT_TYPE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("CONFIG_PRODUCT_TYPE"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].itemCode").value("CURED_PRODUCT"));

        mockMvc.perform(post("/api/v1/settings/dictionaries/defaults/initialize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.category == 'PRODUCT_TYPE')]").exists())
                .andExpect(jsonPath("$.data[?(@.category == 'UNIT')]").exists())
                .andExpect(jsonPath("$.data[?(@.category == 'MATERIAL_CATEGORY')]").exists())
                .andExpect(jsonPath("$.data[?(@.category == 'SAMPLE_STATUS')]").exists())
                .andExpect(jsonPath("$.data[?(@.category == 'ROLE')]").exists());

        mockMvc.perform(get("/api/v1/settings/dictionaries/{category}", "CONFIG_PRODUCT_TYPE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].remark").value("研发样品主分类"));
    }

    @Test
    void nonAdminCannotManageDictionaries() throws Exception {
        var engineerToken = tokenFor("非管理员字典配置", "ou_dictionary_engineer_001", "RND_ENGINEER");

        mockMvc.perform(put("/api/v1/settings/dictionaries/{category}", "CONFIG_PRODUCT_TYPE")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
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
