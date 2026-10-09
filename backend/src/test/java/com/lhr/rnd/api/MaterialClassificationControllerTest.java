package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialClassificationControllerTest extends MaterialCatalogControllerTest {
 @Autowired JdbcTemplate db;
 @Test void directoryMigrationPreservesWaterAndPackagingReferences(){
  var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:directory_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  var sql=new JdbcTemplate(source);
  try {
   org.flywaydb.core.Flyway.configure().dataSource(source).target("37").load().migrate();
   sql.update("insert into material_catalog(id,code,name,category,unit,classification_id,status,version,created_at,updated_at) values('WATER-ID','WATER-CODE','水','PROCESS_WATER','kg','CAT-PROCESS_WATER','ACTIVE',0,current_timestamp,current_timestamp)");
   sql.update("insert into material_catalog(id,code,name,category,unit,classification_id,status,version,created_at,updated_at) values('PACK-ID','PACK-CODE','袋','PACKAGING','个','CAT-PACKAGING','ACTIVE',0,current_timestamp,current_timestamp)");
   org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
   assertThat(sql.queryForObject("select parent_id from material_classification where id='CAT-PROCESS_WATER'",String.class)).isEqualTo("CAT-RAW");
   assertThat(sql.queryForObject("select p.name from material_classification c join material_classification p on p.id=c.parent_id where c.id='CAT-PACKAGING'",String.class)).isEqualTo("包材");
   var catalog=new com.lhr.rnd.service.MaterialCatalogService(sql,null);
   assertThat(catalog.list("WATER-CODE",null,null,1,20,"CAT-RAW").items()).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo("WATER-ID");assertThat(m.category()).isEqualTo("PROCESS_WATER");assertThat(m.classificationId()).isEqualTo("CAT-PROCESS_WATER");});
   assertThat(catalog.list("PACK-CODE",null,null,1,20,"CAT-PACKAGING-ROOT").items()).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo("PACK-ID");assertThat(m.category()).isEqualTo("PACKAGING");assertThat(m.unit()).isEqualTo("个");});
  } finally {sql.execute("SHUTDOWN");}
 }
 @Test void migrationPreservesExistingMaterialIdentityAndType(){
  String url="jdbc:h2:mem:classification_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
  var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa","");var connection=new JdbcTemplate(source);
  try {
   org.flywaydb.core.Flyway.configure().dataSource(source).target("30").load().migrate();
   connection.update("insert into material_catalog(id,code,name,category,unit,status,version,created_at,updated_at) values('OLD-ID','OLD-CODE','旧物料','RAW','kg','ACTIVE',0,current_timestamp,current_timestamp)");
   org.flywaydb.core.Flyway.configure().dataSource(source).target("32").load().migrate();
   assertThat(connection.queryForMap("select id,code,category,classification_id from material_catalog where id='OLD-ID'").values())
    .containsExactlyInAnyOrder("OLD-ID","OLD-CODE","RAW","CAT-RAW");
  } finally {connection.execute("SHUTDOWN");}
 }
 String classification(String token,String parent,String type,String name) throws Exception {
  var body=new java.util.HashMap<String,Object>();body.put("name",name);body.put("businessType",type);body.put("sortOrder",0);if(parent!=null)body.put("parentId",parent);
  var r=mvc.perform(post("/api/v1/material-classifications").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
   .andExpect(status().isOk()).andReturn();
  return mapper.readTree(r.getResponse().getContentAsString()).at("/data/id").asText();
 }
 String body(String parent,String type,String name) throws Exception {return mapper.writeValueAsString(Map.of("parentId",parent,"businessType",type,"name",name,"sortOrder",0,"expectedVersion",0));}
 @Test void assistantCannotManageClassificationEvenWithCapability() throws Exception {
  var admin=token("SYSTEM_ADMIN");var assistant=token("RND_ASSISTANT");
  var id=classification(admin,null,"RAW","权限"+UUID.randomUUID());
  var ruleId="TC-"+UUID.randomUUID().toString().substring(0,20);
  db.update("insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at) values(?,'RND_ASSISTANT','POST','/api/v1/material-classifications',true,'测试',0,current_timestamp)",ruleId);
  try {
   mvc.perform(post("/api/v1/material-classifications").header("Authorization","Bearer "+assistant).contentType(MediaType.APPLICATION_JSON).content(body(id,"RAW","不能新建"))).andExpect(status().isForbidden());
   mvc.perform(get("/api/v1/material-classifications").header("Authorization","Bearer "+assistant)).andExpect(status().isOk());
  } finally {db.update("delete from role_permission where id=?",ruleId);}
 }
 @Test void classificationDepthAndSiblingNamesValidated() throws Exception {
  var t=token("SYSTEM_ADMIN");var root=classification(t,null,"RAW","品种"+UUID.randomUUID());var child=classification(t,root,"RAW","牛");
  mvc.perform(post("/api/v1/material-classifications").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(body(root,"RAW"," 牛 "))).andExpect(jsonPath("$.code").value("CLASSIFICATION_NAME_CONFLICT"));
  mvc.perform(post("/api/v1/material-classifications").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(body(child,"RAW","三级"))).andExpect(jsonPath("$.code").value("CLASSIFICATION_INVALID"));
  var other=classification(t,null,"AUXILIARY","辅料"+UUID.randomUUID()); classification(t,other,"AUXILIARY","牛");
  mvc.perform(put("/api/v1/material-classifications/"+child).header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(body(other,"RAW","牛"))).andExpect(jsonPath("$.code").value("CLASSIFICATION_INVALID"));
 }
 @Test void staleClassificationWriteRejected() throws Exception {
  var t=token("SYSTEM_ADMIN");var id=classification(t,null,"RAW","并发"+UUID.randomUUID());
  for(int i=0;i<2;i++)mvc.perform(put("/api/v1/material-classifications/"+id).header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON)
   .content(mapper.writeValueAsString(Map.of("name","改名"+id,"sortOrder",1,"expectedVersion",0)))).andExpect(jsonPath("$.code").value(i==0?"0":"CLASSIFICATION_VERSION_CONFLICT"));
 }
 @Test void disabledParentBlocksNewAssignmentOnly() throws Exception {
  var t=token("SYSTEM_ADMIN");var root=classification(t,null,"RAW","停用"+UUID.randomUUID());var child=classification(t,root,"RAW","牛");
  var code="CLASS-"+UUID.randomUUID();var m=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(item(code,"RAW","kg"));m.put("classificationId",child);
  var r=mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(m.toString())).andExpect(status().isOk()).andReturn();
  var id=mapper.readTree(r.getResponse().getContentAsString()).at("/data/id").asText();
  mvc.perform(post("/api/v1/material-classifications/"+root+"/status").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"expectedVersion\":0}")).andExpect(status().isOk());
  mvc.perform(put("/api/v1/materials/"+id).header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(m.toString())).andExpect(status().isOk());
  m.put("code",code+"NEW");
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(m.toString())).andExpect(jsonPath("$.code").value("CLASSIFICATION_INVALID"));
  mvc.perform(get("/api/v1/materials").param("query",code).header("Authorization","Bearer "+t)).andExpect(jsonPath("$.data.total").value(1));
 }
 @Test void rootFilterIncludesChildrenAndPackagingCannotPretendToBeRaw() throws Exception {
  var t=token("SYSTEM_ADMIN");var root=classification(t,null,"RAW","筛选"+UUID.randomUUID());var child=classification(t,root,"RAW","牛");
  var m=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(item("FILTER-"+UUID.randomUUID(),"RAW","kg"));m.put("classificationId",child);
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(m.toString())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/materials").param("classificationId",root).header("Authorization","Bearer "+t)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].classificationId").value(child));
  var pack=classification(t,null,"PACKAGING","包材"+UUID.randomUUID());m.put("classificationId",pack);m.put("code","PACK-"+UUID.randomUUID());
  mvc.perform(post("/api/v1/materials").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(m.toString())).andExpect(jsonPath("$.code").value("CLASSIFICATION_INVALID"));
 }
}
