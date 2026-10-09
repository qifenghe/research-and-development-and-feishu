package com.lhr.rnd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lhr.rnd.service.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RequestIntakeControllerTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 private SessionPrincipal actor(String role,String name){return new SessionPrincipal("INTAKE-"+role,role,name,null,role,Instant.now().plusSeconds(600));}
 private ObjectNode payload() throws Exception{return (ObjectNode)json.readTree("""
 {"productName":"代录闭环验收","productType":"预制菜","customerName":"客户","specification":"500g",
 "applicationScenario":"餐饮","flavorRequirement":"香辣","creatorName":"销售张",
 "referenceSampleProvided":false,"cutShape":"20×30 mm","mainMaterialRequirement":"牛腩","textureRequirement":"软烂",
 "packagingRequirement":"真空袋","storageRequirement":"冷冻","customerTargetPrice":"18元/袋","monthlyDemand":"3吨",
 "specialRequirements":"无","recorderName":"伪造录入人"}
 """);}
 @Test void recordsSalesSeparatelyAndReturnsSameRequestThroughRejectResubmitApprove() throws Exception {
  var input=payload();
  var created=mvc.perform(post("/api/v1/sample-requests").requestAttr("sessionPrincipal",actor("RND_ASSISTANT","真实内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isOk())
   .andExpect(jsonPath("$.data.creatorName").value("销售张")).andExpect(jsonPath("$.data.recorderName").value("真实内勤"))
   .andReturn().getResponse().getContentAsString();
  var data=json.readTree(created).path("data");var id=data.path("id").asText();var sampleNo=data.path("sampleNo").asText();
  mvc.perform(post("/api/v1/sample-requests/{id}/reject",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","真实总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"comment\":\"请明确块型\",\"reviewerName\":\"伪造\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
   .andExpect(jsonPath("$.data.reviewVersion").value(1)).andExpect(jsonPath("$.data.reviewHistory[1].actorName").value("真实总监"));
  assertThat(jdbc.queryForObject("select count(*) from sample_project where request_id=?",Integer.class,id)).isZero();
  input.put("cutShape","30×30 mm");input.put("creatorName","销售李");
  var body=json.createObjectNode().put("versionNo",1).set("request",input);
  mvc.perform(put("/api/v1/sample-requests/{id}/resubmit",id).requestAttr("sessionPrincipal",actor("RND_ASSISTANT","真实内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk())
   .andExpect(jsonPath("$.data.id").value(id)).andExpect(jsonPath("$.data.sampleNo").value(sampleNo))
   .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW")).andExpect(jsonPath("$.data.creatorName").value("销售李"));
  mvc.perform(post("/api/v1/sample-requests/{id}/reject",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","真实总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"comment\":\"过期审核\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
  mvc.perform(post("/api/v1/sample-requests/{id}/approve",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","真实总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"reviewerName\":\"旧页面审核\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
  mvc.perform(post("/api/v1/sample-requests/{id}/approve",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","真实总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"reviewerName\":\"伪造\",\"versionNo\":2}"))
   .andExpect(status().isOk());
  mvc.perform(get("/api/v1/sample-requests/{id}",id)).andExpect(status().isOk())
   .andExpect(jsonPath("$.data.recorderName").value("真实内勤"))
   .andExpect(jsonPath("$.data.reviewHistory.length()").value(4))
   .andExpect(jsonPath("$.data.reviewHistory[3].actorName").value("真实总监"));
  assertThat(jdbc.queryForObject("select count(*) from sample_project where request_id=?",Integer.class,id)).isEqualTo(1);
  mvc.perform(post("/api/v1/sample-requests/{id}/approve",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","真实总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":2}"))
   .andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("select count(*) from sample_project where request_id=?",Integer.class,id)).isEqualTo(1);
  mvc.perform(put("/api/v1/sample-requests/{id}/resubmit",id).requestAttr("sessionPrincipal",actor("RND_ASSISTANT","真实内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
 }
 @Test void requiresRejectionCommentAndReviewerRole() throws Exception {
  var created=mvc.perform(post("/api/v1/sample-requests").requestAttr("sessionPrincipal",actor("RND_ASSISTANT","内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(payload().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var id=json.readTree(created).path("data").path("id").asText();
  mvc.perform(post("/api/v1/sample-requests/{id}/reject",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"comment\":\"  \"}"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/sample-requests/{id}/reject",id).requestAttr("sessionPrincipal",actor("RND_ASSISTANT","内勤"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"comment\":\"不能自审\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_REVIEW_FORBIDDEN"));
 }
 @Test void historicalIncompleteRequestCanBeRejectedWithoutInventingRecorder() throws Exception {
  var response=mvc.perform(post("/api/v1/sample-requests").requestAttr("sessionPrincipal",actor("RND_ASSISTANT","内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(payload().toString())).andReturn().getResponse().getContentAsString();
  var id=json.readTree(response).path("data").path("id").asText();
  jdbc.update("delete from sample_request_review where request_id=?",id);
  jdbc.update("delete from sample_request_intake where request_id=?",id);
  jdbc.update("update sample_request set reference_sample_provided=null,cut_shape=null where id=?",id);
  mvc.perform(post("/api/v1/sample-requests/{id}/reject",id).requestAttr("sessionPrincipal",actor("RND_DIRECTOR","总监"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":0,\"comment\":\"补充规格\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.recorderName").isEmpty())
   .andExpect(jsonPath("$.data.reviewHistory.length()").value(1));
 }
 @Test void missingIdentityAndInvalidSalesNameCannotCreateRequest() throws Exception {
  mvc.perform(post("/api/v1/sample-requests").contentType(MediaType.APPLICATION_JSON).content(payload().toString()))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION_PRINCIPAL_REQUIRED"));
  for(var name:java.util.List.of(" ","销".repeat(101))) {
   var input=payload().put("creatorName",name);
   mvc.perform(post("/api/v1/sample-requests").requestAttr("sessionPrincipal",actor("RND_ASSISTANT","内勤"))
    .contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isBadRequest());
  }
 }
 @Test void simultaneousRejectionsOnlyRecordOneDecision() throws Exception {
  var created=mvc.perform(post("/api/v1/sample-requests").requestAttr("sessionPrincipal",actor("RND_ASSISTANT","内勤"))
   .contentType(MediaType.APPLICATION_JSON).content(payload().toString())).andReturn().getResponse().getContentAsString();
  var id=json.readTree(created).path("data").path("id").asText();
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var start=new java.util.concurrent.CountDownLatch(1);
   java.util.concurrent.Callable<Integer> submit=()->{start.await();return mvc.perform(post("/api/v1/sample-requests/{id}/reject",id)
    .requestAttr("sessionPrincipal",actor("RND_DIRECTOR","总监")).contentType(MediaType.APPLICATION_JSON)
    .content("{\"versionNo\":0,\"comment\":\"补充规格\"}")).andReturn().getResponse().getStatus();};
   var one=executor.submit(submit);var two=executor.submit(submit);start.countDown();
   assertThat(java.util.List.of(one.get(10,java.util.concurrent.TimeUnit.SECONDS),two.get(10,java.util.concurrent.TimeUnit.SECONDS)))
    .containsExactlyInAnyOrder(200,400);
   assertThat(jdbc.queryForObject("select count(*) from sample_request_review where request_id=? and action='REJECTED'",Integer.class,id)).isEqualTo(1);
  } finally {executor.shutdownNow();}
 }
}
