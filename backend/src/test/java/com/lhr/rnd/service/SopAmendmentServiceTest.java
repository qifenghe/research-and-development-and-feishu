package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class SopAmendmentServiceTest {
 @Autowired SopAmendmentService amendments;
 @Autowired ProcessPlanService plans;
 @Autowired ProcessRevisionService revisions;
 @Autowired JdbcTemplate db;
 @Autowired ObjectMapper mapper;
 @Autowired SopCatalogService catalog;
 @Autowired ProcessArtifactService artifacts;
 final SessionPrincipal assistant=new SessionPrincipal("SOP-A","RND_ASSISTANT","内勤甲",null,"RND_ASSISTANT",java.time.Instant.now().plusSeconds(3600));
 final SessionPrincipal other=new SessionPrincipal("SOP-B","RND_ASSISTANT","内勤乙",null,"RND_ASSISTANT",java.time.Instant.now().plusSeconds(3600));
 final SessionPrincipal director=new SessionPrincipal("SOP-D","RND_DIRECTOR","总监",null,"RND_DIRECTOR",java.time.Instant.now().plusSeconds(3600));

 @Test void lockedTaskPreservesResearchGraph() throws Exception {
  var revision=seed();var original=plans.find(revision.experimentFormId());
  db.update("update experiment_form set status='COMPLETED' where id=?",revision.experimentFormId());
  var a=amendments.create(revision.experimentFormId(),revision.id(),"车间熟制损耗调整",assistant);
  assertThat(a.status()).isEqualTo("DRAFT");assertThat(a.createdBy()).isEqualTo("内勤甲");
  var changed=change(a.snapshot(),"instruction","煮制结束后沥水5分钟");
  a=amendments.save(a.id(),a.versionNo(),"车间熟制损耗调整","车间记录2026-10-07",changed,assistant);
  assertThat(a.differences()).anyMatch(d->d.fieldPath().endsWith("/instruction"));
  a=amendments.submit(a.id(),a.versionNo(),assistant);
  assertThat(a.status()).isEqualTo("PENDING_REVIEW");
  assertThat(plans.find(revision.experimentFormId())).isEqualTo(original);
  assertThat(revisions.find(revision.experimentFormId(),revision.id())).isEqualTo(revision);
  assertThatThrownBy(()->amendments.create(revision.experimentFormId(),revision.id(),"另一次",assistant)).isInstanceOf(BusinessException.class);
  final var frozen=a;
  assertThatThrownBy(()->amendments.save(frozen.id(),frozen.versionNo(),"调整",null,changed,assistant)).isInstanceOf(BusinessException.class);
 }
 @Test void assistantOnlyEditsOwnDraftAndCannotSubmitWithoutChanges(){
  var revision=seed();var a=amendments.create(revision.experimentFormId(),revision.id(),"车间参数调整",assistant);
  assertThatThrownBy(()->amendments.get(a.id(),other)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->amendments.submit(a.id(),a.versionNo(),assistant)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->amendments.save(a.id(),a.versionNo()," ",null,a.snapshot(),assistant)).isInstanceOf(BusinessException.class);
  assertThat(amendments.list("",null,other)).noneMatch(r->r.id().equals(a.id()));
  assertThat(amendments.get(a.id(),director).id()).isEqualTo(a.id());
  amendments.cancel(a.id(),a.versionNo(),assistant);
  assertThat(amendments.create(revision.experimentFormId(),revision.id(),"重新调整",assistant).id()).isNotEqualTo(a.id());
 }
 @Test void saveRecalculatesYieldAndRejectsStaleVersion() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"沥水后称重",assistant);
  var tree=mapper.valueToTree(a.snapshot());
  ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0/outputs/0")).put("weightKg",9);
  ((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("batchYieldPercent",999);
  var changed=mapper.treeToValue(tree,ProcessPlan.class);
  var saved=amendments.save(a.id(),a.versionNo(),"沥水后称重",null,changed,assistant);
  assertThat(saved.snapshot().batchYieldPercent()).isEqualByComparingTo("90");
  assertThat(saved.differences()).anyMatch(d->d.derived());
  assertThatThrownBy(()->amendments.save(a.id(),a.versionNo(),"沥水",null,changed,assistant)).isInstanceOf(BusinessException.class);
 }
 @Test void invalidIntermediateReferenceCannotBeSubmitted() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"流转调整",assistant);
  var tree=mapper.valueToTree(a.snapshot());var m=(com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0/materials/0");
  m.put("sourceType","STEP_OUTPUT");m.put("sourceStepOutputId","MISSING");
  var changed=mapper.treeToValue(tree,ProcessPlan.class);
  assertThatThrownBy(()->{var saved=amendments.save(a.id(),a.versionNo(),"流转调整",null,changed,assistant);amendments.submit(saved.id(),saved.versionNo(),assistant);}).isInstanceOf(BusinessException.class);
 }
 @Test void savedSnapshotUsesCurrentReason() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"初步调整",assistant);
  var saved=amendments.save(a.id(),a.versionNo(),"车间沥水记录确认",null,change(a.snapshot(),"instruction","沥水5分钟"),assistant);
  assertThat(saved.snapshot().changeReason()).isEqualTo(saved.reason());
 }
 @Test void assistantCatalogExposesOnlyFormalSnapshots(){
  var r=seed();var result=catalog.list("",1,100,assistant);
  assertThat(result.items()).anyMatch(item->item.formId().equals(r.experimentFormId()));
  assertThatThrownBy(()->plans.requireDraftReadAccess(r.experimentFormId(),assistant)).isInstanceOf(BusinessException.class);
 }
 @Test void assistantSopExportDoesNotGrantPrivateArtifactAccess(){
  var r=seed();
  assertThatThrownBy(()->artifacts.list(r.experimentFormId(),r.id(),assistant)).isInstanceOf(BusinessException.class);
  assertThat(artifacts.listForSop(r.experimentFormId(),r.id(),assistant)).isEmpty();
  var file=artifacts.generateForSop(r.experimentFormId(),r.id(),"SOP_DOCX",assistant);
  assertThat(file.status()).isEqualTo("READY");
  assertThat(artifacts.downloadForSop(r.experimentFormId(),r.id(),file.id(),assistant).content()).isNotEmpty();
  assertThatThrownBy(()->artifacts.generateForSop(r.experimentFormId(),r.id(),"FORMULA_XLSX",assistant)).isInstanceOf(BusinessException.class);
 }
 @Test void assistantFormalBundleReadsArchivedFormulaWithoutGrantingGenerationOrOtherRevisionAccess(){
  var r=seed();var formula=artifacts.generate(r.experimentFormId(),r.id(),"FORMULA_XLSX",director);
  assertThat(formula.status()).isEqualTo("READY");
  var before=artifacts.download(r.experimentFormId(),r.id(),formula.id(),director).content();
  assertThat(artifacts.listForSop(r.experimentFormId(),r.id(),assistant)).anyMatch(f->f.id().equals(formula.id()));
  assertThat(artifacts.downloadForSop(r.experimentFormId(),r.id(),formula.id(),assistant).content()).isEqualTo(before);
  var other=seed();
  assertThatThrownBy(()->artifacts.downloadForSop(other.experimentFormId(),other.id(),formula.id(),assistant)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->artifacts.generateForSop(r.experimentFormId(),r.id(),"FORMULA_XLSX",assistant)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->artifacts.list(r.experimentFormId(),r.id(),assistant)).isInstanceOf(BusinessException.class);
 }
 ProcessPlan change(ProcessPlan plan,String field,String value) throws Exception {
  var tree=mapper.valueToTree(plan);((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0")).put(field,value);
  return mapper.treeToValue(tree,ProcessPlan.class);
 }
 com.lhr.rnd.model.ProcessRevision seed(){
  var id=UUID.randomUUID().toString().replace("-","");var now=LocalDateTime.now();
  db.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)",id,id,"牛腩","预制菜","客户","1kg","销售","PENDING_ASSIGNMENT",now);
  db.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)",id,id,id,"牛腩","预制菜","客户","1kg","ACTIVE",now);
  db.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)",id,id,id,"牛腩","预制菜","1kg","1",1,"V1",now);
  db.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)",id,id,id,id,"牛腩","V1","SAMPLING",now);
  db.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)",id,id,id,id,id,"牛腩","V1","DRAFT","研发",now);
  var material=com.lhr.rnd.MaterialCatalogFixtures.material(db,null,1,"PRIMARY","BEEF","牛腩","SOLID",BigDecimal.TEN,null,null,"EXTERNAL",null);
  var step=new ProcessPlan.MinorStep(null,1,"COOK","煮制","NORMAL",null,null,null,null,null,null,null,"煮制",List.of(material),List.of(new ProcessPlan.StepOutput("O-"+id,1,"FINISHED","熟牛腩","SOLID",BigDecimal.TEN,true,false,null)),List.of());
  var major=new ProcessPlan.MajorProcess(null,1,"COOK","熟制",null,"PRIMARY_INPUT",null,List.of(step),List.of(),List.of(),null);
  var saved=plans.save(id,new ProcessPlan(null,id,plans.find(id).versionNo(),"DRAFT",List.of(major),null,false));
  return revisions.submit(id,new ProcessRevisionService.SubmitCommand(saved.versionNo(),true,"首次提交","研发"));
 }
}
