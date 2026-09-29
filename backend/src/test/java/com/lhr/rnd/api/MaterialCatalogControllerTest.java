package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="rnd.session.auth-required=true") @AutoConfigureMockMvc @ActiveProfiles("test")
class MaterialCatalogControllerTest {
 @Test void listReturnsServerCapabilities() throws Exception {
  mvc.perform(get("/api/v1/materials").header("Authorization","Bearer "+token("RND_ASSISTANT")))
   .andExpect(jsonPath("$.data.access.create").value(true)).andExpect(jsonPath("$.data.access.classifications").value(false));
  mvc.perform(get("/api/v1/materials").header("Authorization","Bearer "+token("RND_ENGINEER")))
   .andExpect(jsonPath("$.data.access.create").value(false));
 }
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
 String token(String role) throws Exception {
  var uid="mat-"+UUID.randomUUID();
  String body=mapper.writeValueAsString(java.util.Map.of("name","物料测试","feishuUserId",uid,"role",role));
  mvc.perform(post("/api/v1/feishu/users/bind").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  var result=mvc.perform(post("/api/v1/feishu/oauth/callback").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("code","mock:"+uid)))).andExpect(status().isOk()).andReturn();
  return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
 }
 String item(String code,String category,String unit) throws Exception {return mapper.writeValueAsString(java.util.Map.of("code",code,"name","测试物料","category",category,"unit",unit,"specification","测试规格","expectedVersion",0));}
 @Test void normalizedCodeIsUnique() throws Exception {
  var t=token("SYSTEM_ADMIN");var code="m"+UUID.randomUUID();
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item(" "+code+" ","RAW","kg"))).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.code").value(code.toUpperCase()));
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item(code.toUpperCase(),"RAW","kg"))).andExpect(jsonPath("$.code").value("MATERIAL_CODE_CONFLICT"));
 }
 @Test void engineerCannotManage() throws Exception {
  var t=token("RND_ENGINEER");mvc.perform(get("/api/v1/materials").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item("FORBIDDEN","RAW","kg"))).andExpect(status().isForbidden());
 }
 @Test void staleVersionRejected() throws Exception {
  var t=token("RND_ASSISTANT");var code="v"+UUID.randomUUID();var r=mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item(code,"RAW","kg"))).andExpect(jsonPath("$.code").value("0")).andReturn();
  var id=mapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asText();
  for(int i=0;i<2;i++) mvc.perform(put("/api/v1/materials/"+id).header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item(code,"RAW","kg"))).andExpect(jsonPath("$.code").value(i==0?"0":"MATERIAL_VERSION_CONFLICT"));
 }
 @Test void unitCategoryValidated() throws Exception {
  var t=token("SYSTEM_ADMIN");mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item("bad"+UUID.randomUUID(),"RAW","L"))).andExpect(jsonPath("$.code").value("MATERIAL_INVALID"));
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(item("pack"+UUID.randomUUID(),"PACKAGING","米"))).andExpect(jsonPath("$.code").value("0"));
 }
}
