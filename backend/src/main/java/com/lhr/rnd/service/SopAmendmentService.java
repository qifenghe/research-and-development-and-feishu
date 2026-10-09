package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.SopAmendmentDiff;
import com.lhr.rnd.domain.StepParameterService;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.SopAmendment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** Independent SOP evidence; never saves into the research workspace. */
@Service
public class SopAmendmentService {
 private final JdbcTemplate db;
 private final ObjectMapper mapper;
 private final ProcessRevisionService revisions;
 private final ProcessPlanService plans;
 private final RolePermissionService permissions;
 private final MaterialReferenceService materials;
 private final FormalRevisionWriter writer;
 private final SopAmendmentDiff diff=new SopAmendmentDiff();
 private final ProcessPlanCalculationService calculations=new ProcessPlanCalculationService();
 public SopAmendmentService(JdbcTemplate db,ObjectMapper mapper,ProcessRevisionService revisions,ProcessPlanService plans,RolePermissionService permissions,MaterialReferenceService materials,FormalRevisionWriter writer){
  this.db=db;this.mapper=mapper;this.revisions=revisions;this.plans=plans;this.permissions=permissions;this.materials=materials;this.writer=writer;
 }
 @Transactional public SopAmendment create(String formId,String sourceId,String reason,SessionPrincipal actor){
  allow(actor,"POST","/api/v1/sop-amendments",true);reason=text(reason,true);
  lockForm(formId);var source=revisions.find(formId,sourceId);
  if(!Objects.equals(revisions.latestOrNull(formId).id(),sourceId))throw failure("SOP_SOURCE_STALE","请从最新正式版本创建修订");
  if(db.queryForObject("select count(*) from sop_amendment_active where form_id=?",Integer.class,formId)>0)throw failure("SOP_ACTIVE_EXISTS","该产品已有未结束修订单，请先处理");
  var id="SOPA-"+UUID.randomUUID();var now=LocalDateTime.now();var json=serialize(source.snapshot());
  db.update("insert into sop_amendment(id,form_id,source_revision_id,source_hash,version_no,status,reason,created_by_id,created_by,created_at,updated_at,snapshot_json,snapshot_hash) values (?,?,?,?,0,'DRAFT',?,?,?,?,?,?,?)",id,formId,sourceId,source.snapshotHash(),reason,actor.userId(),actor.name(),now,now,json,hash(json));
  db.update("insert into sop_amendment_active(form_id,amendment_id) values (?,?)",formId,id);
  history(id,0,"CREATED",actor,reason);return get(id,actor);
 }
 @Transactional(readOnly=true) public SopAmendment get(String id,SessionPrincipal actor){
  allow(actor,"GET","/api/v1/sop-amendments/"+id,false);var a=load(id);
  if("RND_ASSISTANT".equals(actor.role())&&!actor.userId().equals(a.createdById()))throw forbidden();
  return a;
 }
 @Transactional(readOnly=true) public List<SopAmendment> list(String keyword,String status,SessionPrincipal actor){
  allow(actor,"GET","/api/v1/sop-amendments",false);
  String query="select a.id from sop_amendment a join experiment_form f on f.id=a.form_id where locate(lower(?),lower(f.product_name))>0";
  var args=new ArrayList<Object>();args.add(keyword==null?"":keyword.trim());
  if(status!=null&&!status.isBlank()){query+=" and a.status=?";args.add(status);}
  if("RND_ASSISTANT".equals(actor.role())){query+=" and a.created_by_id=?";args.add(actor.userId());}
  return db.query(query+" order by a.updated_at desc,a.id",(rs,n)->rs.getString(1),args.toArray()).stream().map(this::load).toList();
 }
 @Transactional public SopAmendment save(String id,int version,String reason,String evidence,ProcessPlan incoming,SessionPrincipal actor){
  allow(actor,"PUT","/api/v1/sop-amendments/"+id,true);var a=lock(id,version);own(a,actor);editable(a);reason=text(reason,true);evidence=text(evidence,false);
  if(incoming==null)throw failure("SOP_SNAPSHOT_REQUIRED","请填写修订内容");
  var snapshot=normalize(a,incoming,reason);var json=serialize(snapshot);
  diff.compare(mapper.valueToTree(revisions.find(a.formId(),a.sourceRevisionId()).snapshot()),mapper.valueToTree(snapshot));
  int next=version+1;
  db.update("update sop_amendment set version_no=?,reason=?,evidence_note=?,snapshot_json=?,snapshot_hash=?,updated_at=current_timestamp where id=?",next,reason,evidence,json,hash(json),id);
  history(id,next,"SAVED",actor,null);return get(id,actor);
 }
 @Transactional public SopAmendment submit(String id,int version,SessionPrincipal actor){
  allow(actor,"POST","/api/v1/sop-amendments/"+id+"/submit",true);var a=lock(id,version);own(a,actor);editable(a);currentSource(a);
  if(a.differences().stream().noneMatch(d->!d.derived()))throw failure("SOP_NO_CHANGES","尚无实际修改，无需提交审核");
  var snapshot=normalize(a,a.snapshot());ready(snapshot);
  var json=serialize(snapshot);
  db.update("update sop_amendment set status='PENDING_REVIEW',version_no=?,snapshot_json=?,snapshot_hash=?,submitted_hash=?,updated_at=current_timestamp where id=?",version+1,json,hash(json),hash(json),id);
  history(id,version+1,"SUBMITTED",actor,a.reason());return get(id,actor);
 }
 @Transactional public SopAmendment cancel(String id,int version,SessionPrincipal actor){
  allow(actor,"POST","/api/v1/sop-amendments/"+id+"/cancel",true);var a=lock(id,version);own(a,actor);editable(a);
  db.update("update sop_amendment set status='CANCELLED',version_no=?,updated_at=current_timestamp where id=?",version+1,id);
  db.update("delete from sop_amendment_active where amendment_id=?",id);history(id,version+1,"CANCELLED",actor,null);return get(id,actor);
 }
 private ProcessPlan normalize(SopAmendment a,ProcessPlan incoming){
  return normalize(a,incoming,a.reason());
 }
 private ProcessPlan normalize(SopAmendment a,ProcessPlan incoming,String reason){
  var resolved=materials.resolvePlan(incoming,a.snapshot());
  var majors=values(resolved.majorProcesses()).stream().map(m->{
   values(m.steps()).forEach(StepParameterService::validate);
   return new ProcessPlan.MajorProcess(m.id(),m.sequence(),m.processCode(),m.processName(),m.description(),m.yieldBasis(),m.remark(),m.steps(),m.inputs(),m.outputs(),calculations.calculate(m));
  }).toList();
  var p=new ProcessPlan(a.snapshot().id(),a.formId(),a.versionNo(),"DRAFT",majors,null,resolved.balanceToleranceKg(),resolved.legacy(),a.sourceRevisionId(),reason);
  return new ProcessPlan(p.id(),p.experimentFormId(),p.versionNo(),p.status(),p.majorProcesses(),calculations.calculateBatch(p),p.balanceToleranceKg(),p.legacy(),p.sourceRevisionId(),p.changeReason());
 }
 public record Publication(SopAmendment amendment,com.lhr.rnd.model.ProcessRevision revision){}
 @Transactional public SopAmendment returnForChanges(String id,int version,String comment,SessionPrincipal actor){
  reviewer(actor,"/api/v1/sop-amendments/"+id+"/return");comment=text(comment,true);var a=lock(id,version);pending(a);
  db.update("update sop_amendment set status='RETURNED',version_no=?,submitted_hash=null,updated_at=current_timestamp where id=?",version+1,id);
  history(id,version+1,"RETURNED",actor,comment);return get(id,actor);
 }
 @Transactional public Publication approve(String id,int version,boolean confirmed,SessionPrincipal actor){
  reviewer(actor,"/api/v1/sop-amendments/"+id+"/approve");
  if(!confirmed)throw failure("SOP_CONFIRM_REQUIRED","请明确确认发布");
  var a=lock(id,version);pending(a);currentSource(a);
  var frozen=db.queryForObject("select submitted_hash from sop_amendment where id=?",String.class,id);
  if(!Objects.equals(frozen,hash(serialize(a.snapshot()))))throw failure("SOP_SNAPSHOT_DAMAGED","待审核内容已变化，不能发布");
  var snapshot=normalize(a,a.snapshot());ready(snapshot);
  var revision=writer.append(a.formId(),snapshot,a.sourceRevisionId(),a.reason(),actor);
  db.update("update sop_amendment set status='PUBLISHED',version_no=?,published_revision_id=?,updated_at=current_timestamp where id=?",version+1,revision.id(),id);
  db.update("delete from sop_amendment_active where amendment_id=?",id);history(id,version+1,"PUBLISHED",actor,a.reason());
  return new Publication(get(id,actor),revision);
 }
 private void reviewer(SessionPrincipal p,String path){allow(p,"POST",path,false);if(!"RND_DIRECTOR".equals(p.role()))throw forbidden();}
 private void pending(SopAmendment a){if(!"PENDING_REVIEW".equals(a.status()))throw failure("SOP_STATE_CONFLICT","只有待审核修订单可以审核");}
 private void ready(ProcessPlan snapshot){
  var check=plans.submissionCheck(snapshot);if(!check.ready())throw failure("SOP_NOT_READY","修订内容未通过完整校验："+check.errors().get(0).message());
 }
 private SopAmendment load(String id){
  var rows=db.query("select * from sop_amendment where id=?",(rs,n)->{
   var json=rs.getString("snapshot_json");if(!hash(json).equals(rs.getString("snapshot_hash")))throw failure("SOP_SNAPSHOT_DAMAGED","修订内容完整性校验失败");
   var snapshot=deserialize(json);var source=revisions.find(rs.getString("form_id"),rs.getString("source_revision_id"));
   if(!source.snapshotHash().equals(rs.getString("source_hash")))throw failure("SOP_SOURCE_DAMAGED","来源版本校验失败");
   var history=db.query("select * from sop_amendment_history where amendment_id=? order by version_no",(r,k)->new SopAmendment.History(r.getInt("version_no"),r.getString("action"),r.getString("actor_id"),r.getString("actor_name"),r.getString("comment"),r.getTimestamp("acted_at").toLocalDateTime().toString()),id);
   return new SopAmendment(id,rs.getString("form_id"),source.id(),source.snapshotHash(),rs.getInt("version_no"),rs.getString("status"),rs.getString("reason"),rs.getString("evidence_note"),rs.getString("created_by_id"),rs.getString("created_by"),rs.getTimestamp("created_at").toLocalDateTime().toString(),rs.getTimestamp("updated_at").toLocalDateTime().toString(),rs.getString("published_revision_id"),snapshot,diff.compare(mapper.valueToTree(source.snapshot()),mapper.valueToTree(snapshot)),history);
  },id);
  if(rows.isEmpty())throw failure("SOP_AMENDMENT_NOT_FOUND","修订单不存在");return rows.get(0);
 }
 private SopAmendment lock(String id,int version){
  var forms=db.query("select form_id from sop_amendment where id=?",(rs,n)->rs.getString(1),id);
  if(forms.isEmpty())throw failure("SOP_AMENDMENT_NOT_FOUND","修订单不存在");lockForm(forms.get(0));
  db.query("select id from sop_amendment where id=? for update",(rs,n)->rs.getString(1),id);
  var a=load(id);if(a.versionNo()!=version)throw failure("SOP_VERSION_CONFLICT","修订单已更新，请刷新后操作；未覆盖你的输入");return a;
 }
 private void lockForm(String id){if(db.query("select id from experiment_form where id=? for update",(rs,n)->rs.getString(1),id).isEmpty())throw failure("EXPERIMENT_FORM_NOT_FOUND","工艺来源不存在");}
 private void currentSource(SopAmendment a){if(!Objects.equals(revisions.latestOrNull(a.formId()).id(),a.sourceRevisionId()))throw failure("SOP_SOURCE_STALE","正式版本已更新，请保留当前内容并从最新版本重新创建修订");}
 private void editable(SopAmendment a){if(!List.of("DRAFT","RETURNED").contains(a.status()))throw failure("SOP_STATE_CONFLICT","当前状态不能修改");}
 private void own(SopAmendment a,SessionPrincipal p){if(!a.createdById().equals(p.userId()))throw forbidden();}
 private void allow(SessionPrincipal p,String method,String path,boolean write){
  if(p==null||p.userId()==null||p.userId().isBlank()||p.name()==null||p.name().isBlank())throw failure("SESSION_PRINCIPAL_REQUIRED","请先登录");
  if(!(write?"RND_ASSISTANT".equals(p.role()):List.of("RND_ASSISTANT","RND_DIRECTOR").contains(p.role()))||!permissions.hasPermission(p.role(),method,path))throw forbidden();
 }
 private void history(String id,int version,String action,SessionPrincipal p,String comment){db.update("insert into sop_amendment_history(amendment_id,version_no,action,actor_id,actor_name,comment,acted_at) values (?,?,?,?,?,?,current_timestamp)",id,version,action,p.userId(),p.name(),comment);}
 private String text(String value,boolean required){if(value==null||value.isBlank()){if(required)throw failure("SOP_REASON_REQUIRED","请填写调整原因");return null;}if(value.length()>2000)throw failure("SOP_TEXT_TOO_LONG","说明最多2000字");return value.trim();}
 private String serialize(ProcessPlan p){try{return mapper.writeValueAsString(p);}catch(Exception e){throw new IllegalStateException(e);}}
 private ProcessPlan deserialize(String json){try{return mapper.readValue(json,ProcessPlan.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private String hash(String json){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static <T> List<T> values(List<T> list){return list==null?List.of():list;}
 private BusinessException forbidden(){return failure("SOP_FORBIDDEN","当前身份无权操作此SOP修订单");}
 private BusinessException failure(String code,String message){return new BusinessException(code,message);}
}
