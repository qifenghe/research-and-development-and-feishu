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
class FormFieldSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanSaveReadAndInitializeFormFieldsWithoutOverwritingExistingForm() throws Exception {
        var adminToken = tokenFor("表单字段管理员", "ou_form_field_admin_001", "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/v1/settings/form-fields/{formCode}", "CONFIG_SAMPLE_REQUEST")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": [
                                    {
                                      "fieldCode": "productName",
                                      "fieldLabel": "产品名称",
                                      "controlType": "TEXT",
                                      "required": true,
                                      "enabled": true,
                                      "sortOrder": 10,
                                      "placeholder": "请输入样品名称"
                                    },
                                    {
                                      "fieldCode": "productType",
                                      "fieldLabel": "产品类型",
                                      "controlType": "SELECT",
                                      "required": true,
                                      "enabled": true,
                                      "sortOrder": 20,
                                      "dictionaryCategory": "PRODUCT_TYPE"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formCode").value("CONFIG_SAMPLE_REQUEST"))
                .andExpect(jsonPath("$.data.fields.length()").value(2))
                .andExpect(jsonPath("$.data.fields[0].fieldCode").value("productName"))
                .andExpect(jsonPath("$.data.fields[1].dictionaryCategory").value("PRODUCT_TYPE"));

        mockMvc.perform(get("/api/v1/settings/form-fields/{formCode}", "CONFIG_SAMPLE_REQUEST")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formCode").value("CONFIG_SAMPLE_REQUEST"))
                .andExpect(jsonPath("$.data.fields.length()").value(2))
                .andExpect(jsonPath("$.data.fields[0].placeholder").value("请输入样品名称"));

        mockMvc.perform(post("/api/v1/settings/form-fields/defaults/initialize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.formCode == 'SAMPLE_REQUEST')]").exists())
                .andExpect(jsonPath("$.data[?(@.formCode == 'EXPERIMENT_FORM')]").exists())
                .andExpect(jsonPath("$.data[?(@.formCode == 'TEST_RECORD')]").exists())
                .andExpect(jsonPath("$.data[?(@.formCode == 'SHIPMENT_FEEDBACK')]").exists())
                .andExpect(jsonPath("$.data[?(@.formCode == 'PRICING_FILE')]").exists());

        mockMvc.perform(get("/api/v1/settings/form-fields/{formCode}", "CONFIG_SAMPLE_REQUEST")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields.length()").value(2))
                .andExpect(jsonPath("$.data.fields[0].fieldLabel").value("产品名称"));
    }

    @Test
    void nonAdminCannotManageFormFields() throws Exception {
        var engineerToken = tokenFor("非管理员表单配置", "ou_form_field_engineer_001", "RND_ENGINEER");

        mockMvc.perform(put("/api/v1/settings/form-fields/{formCode}", "CONFIG_SAMPLE_REQUEST")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
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
