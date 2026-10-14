package com.lhr.rnd.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** Single allocation lock shared by research submission and reviewed SOP publication. */
@Service
public class FormalRevisionWriter {
 private final JdbcTemplate db;private final ObjectMapper mapper;
 public FormalRevisionWriter(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
 @Transactional public ProcessRevision append(String formId,ProcessPlan snapshot,String sourceId,String reason,SessionPrincipal actor){
  if(actor==null||actor.name()==null||actor.name().isBlank())throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","发布必须使用可信身份");
  return appendTrusted(formId,snapshot,sourceId,reason,actor.name());
 }
 ProcessRevision appendTrusted(String formId,ProcessPlan snapshot,String sourceId,String reason,String actorName){
  if(db.query("select id from experiment_form where id=? for update",(rs,n)->rs.getString(1),formId).isEmpty())throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND","实验单不存在");
  if(!formId.equals(snapshot.experimentFormId()))throw new BusinessException("SOP_FORM_MISMATCH","工艺来源不匹配");
  var p=new ProcessPlan(snapshot.id(),formId,snapshot.versionNo(),"SUBMITTED",snapshot.majorProcesses(),snapshot.batchYieldPercent(),snapshot.balanceToleranceKg(),snapshot.legacy(),sourceId,reason);
  String json,hash;
  try{json=mapper.writeValueAsString(p);hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  var number=db.queryForObject("select coalesce(max(revision_no),0)+1 from experiment_process_revision where experiment_form_id=?",Integer.class,formId);
  var id="PREV-"+UUID.randomUUID();var now=LocalDateTime.now();
  db.update("insert into experiment_process_revision(id,process_plan_id,experiment_form_id,revision_no,source_revision_id,change_reason,submitted_by,submitted_at,snapshot_json,snapshot_hash) values (?,?,?,?,?,?,?,?,?,?)",id,p.id(),formId,number,sourceId,reason,actorName,now,json,hash);
  db.update("update experiment_process_revision set compiled_by=?,compiled_at=? where id=?",actorName,now,id);
  var finished=db.query("select finished_output_quantity,finished_output_unit from experiment_form where id=?",(rs,n)->new com.lhr.rnd.model.TrialScheme.FinishedOutput(rs.getBigDecimal(1),rs.getString(2),null),formId).get(0);
  try { db.update("update experiment_process_revision set finished_output_json=? where id=?",mapper.writeValueAsString(finished),id); }
  catch(Exception e){throw new IllegalStateException("保存正式成品记录失败",e);}
  return new ProcessRevision(id,p.id(),formId,number,sourceId,reason,actorName,now.toString(),hash,p);
 }
}
