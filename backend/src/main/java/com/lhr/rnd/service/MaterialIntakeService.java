package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.MaterialCatalogItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;

@Service
public class MaterialIntakeService {
 public record Line(String temporaryMaterialId,String code,String name,String classificationId,String specification,BigDecimal totalWeightKg,List<String> rowIds,String catalogMaterialId,BigDecimal quantity,String quantityUnit,String materialRole){
  public Line(String temporaryMaterialId,String code,String name,String classificationId,String specification,BigDecimal totalWeightKg,List<String> rowIds,String catalogMaterialId){this(temporaryMaterialId,code,name,classificationId,specification,totalWeightKg,rowIds,catalogMaterialId,null,null,null);}
 }
 public record Request(String id,String formId,String trialId,int sourceVersion,String applicantId,String applicantName,String status,int version,String comment,String reviewerName,List<Line> lines){}
 public record ProposedLine(String temporaryMaterialId,String code){}
 public record Write(String formId,String trialId,int sourceVersion,Integer expectedVersion,List<ProposedLine> lines){}
 public record Decision(String lineId,String mode,String catalogMaterialId){}
 public record Review(Integer expectedVersion,List<Decision> decisions,String comment){}
 private final JdbcTemplate db;private final ObjectMapper mapper;private final MaterialIntakeSourceService source;private final MaterialCatalogService catalog;private final AuditLogService audit;private final MaterialClassificationService classifications;
 public MaterialIntakeService(JdbcTemplate d,ObjectMapper m,MaterialIntakeSourceService s,MaterialCatalogService c,AuditLogService a,MaterialClassificationService cls){db=d;mapper=m;source=s;catalog=c;audit=a;classifications=cls;}
 private BusinessException invalid(String message){return new BusinessException("MATERIAL_INTAKE_INVALID",message);}
 private void actor(SessionPrincipal p){if(p==null||p.userId()==null||!List.of("RND_ENGINEER","RND_ASSISTANT","RND_DIRECTOR").contains(p.role()))throw new BusinessException("MATERIAL_INTAKE_FORBIDDEN","当前角色无权操作物料申请");}
 private void director(SessionPrincipal p){actor(p);if(!"RND_DIRECTOR".equals(p.role()))throw new BusinessException("MATERIAL_INTAKE_FORBIDDEN","只有总监可以审核入库");}
 private Request load(String id,boolean lock){return db.query("select payload_json from material_intake_request where id=?"+(lock?" for update":""),(r,n)->decode(r.getString(1)),id).stream().findFirst().orElseThrow(()->invalid("申请不存在"));}
 public Request find(String id,SessionPrincipal p){actor(p);var r=load(id,false);if(!p.userId().equals(r.applicantId())&&!"RND_DIRECTOR".equals(p.role()))throw new BusinessException("MATERIAL_INTAKE_FORBIDDEN","只能查看本人申请");return r;}
 public List<Request> list(String query,String status,SessionPrincipal p){actor(p);return db.query("select payload_json from material_intake_request order by updated_at desc",(r,n)->decode(r.getString(1))).stream().filter(r->"RND_DIRECTOR".equals(p.role())||p.userId().equals(r.applicantId())).filter(r->status==null||status.isBlank()||status.equals(r.status())).filter(r->query==null||query.isBlank()||r.applicantName().contains(query)||r.lines().stream().anyMatch(l->l.name().contains(query))).toList();}
 @Transactional public Request create(Write w,SessionPrincipal p){actor(p);var lines=snapshot(w,p);var r=new Request("MI-"+UUID.randomUUID(),w.formId(),w.trialId(),w.sourceVersion(),p.userId(),p.name(),"DRAFT",0,null,null,lines);db.update("insert into material_intake_request(id,payload_json,updated_at) values (?,?,current_timestamp)",r.id(),encode(r));record(r,"CREATE",p);return r;}
 @Transactional public Request update(String id,Write w,SessionPrincipal p){var old=owned(id,p,w.expectedVersion());if(!List.of("DRAFT","RETURNED").contains(old.status()))throw invalid("待审核申请不能编辑");if(!Objects.equals(old.formId(),w.formId())||!Objects.equals(old.trialId(),w.trialId()))throw invalid("不可更换来源方案");return save(new Request(id,old.formId(),old.trialId(),w.sourceVersion(),old.applicantId(),old.applicantName(),"DRAFT",old.version()+1,old.comment(),old.reviewerName(),snapshot(w,p)),"UPDATE",p);}
 @Transactional public Request submit(String id,Integer version,SessionPrincipal p){var old=owned(id,p,version);if(!List.of("DRAFT","RETURNED").contains(old.status()))throw invalid("申请当前不能提交");var w=new Write(old.formId(),old.trialId(),old.sourceVersion(),version,old.lines().stream().map(l->new ProposedLine(l.temporaryMaterialId(),l.code())).toList());return save(new Request(id,old.formId(),old.trialId(),old.sourceVersion(),old.applicantId(),old.applicantName(),"PENDING",old.version()+1,old.comment(),old.reviewerName(),snapshot(w,p)),"SUBMIT",p);}
 @Transactional public Request approve(String id,Review review,SessionPrincipal p){
  director(p);var old=load(id,true);pending(old,review.expectedVersion());
  var current=source.loadEditableSource(old.formId(),old.trialId(),p);if(current.version()!=old.sourceVersion())throw invalid("来源方案已修改，请退回重新整理");
  var fresh=snapshot(new Write(old.formId(),old.trialId(),old.sourceVersion(),null,old.lines().stream().map(l->new ProposedLine(l.temporaryMaterialId(),l.code())).toList()),p);
  if(!fresh.equals(old.lines()))throw invalid("来源物料已变化，请重新整理");
  var decisions=new HashMap<String,Decision>();for(var d:review.decisions()==null?List.<Decision>of():review.decisions())if(decisions.put(d.lineId(),d)!=null)throw invalid("审核物料重复");
  if(decisions.size()!=old.lines().size())throw invalid("请确认全部物料的处理方式");
  var bindings=new HashMap<String,MaterialCatalogItem>();var approved=new ArrayList<Line>();
  for(var line:old.lines()){
   var d=decisions.get(line.temporaryMaterialId());if(d==null)throw invalid("物料未审核");
   MaterialCatalogItem item;
   boolean packaging="PACKAGING".equals(line.materialRole());
   var unit=packaging?line.quantityUnit():"kg";
   if("CREATE".equals(d.mode()))item=catalog.create(new MaterialCatalogService.Write(line.code(),line.name(),null,line.specification(),unit,null,line.classificationId()),p);
   else if("LINK_EXISTING".equals(d.mode())){item=catalog.find(d.catalogMaterialId());if(!"ACTIVE".equals(item.status())||packaging!= "PACKAGING".equals(item.category())||!Objects.equals(unit,item.unit()))throw invalid("请选择类型及单位一致的启用物料");}
   else throw invalid("审核处理方式无效");
   bindings.put(line.temporaryMaterialId(),item);approved.add(new Line(line.temporaryMaterialId(),line.code(),line.name(),line.classificationId(),line.specification(),line.totalWeightKg(),line.rowIds(),item.id(),line.quantity(),line.quantityUnit(),line.materialRole()));
  }
  source.applyApprovedReferences(current,bindings,p);
  return save(new Request(id,old.formId(),old.trialId(),old.sourceVersion(),old.applicantId(),old.applicantName(),"APPROVED",old.version()+1,review.comment(),p.name(),approved),"APPROVE",p);
 }
 @Transactional public Request reject(String id,Integer version,String comment,SessionPrincipal p){director(p);if(comment==null||comment.isBlank()||comment.length()>2000)throw invalid("请填写退回批语（最多2000字）");var old=load(id,true);pending(old,version);return save(new Request(id,old.formId(),old.trialId(),old.sourceVersion(),old.applicantId(),old.applicantName(),"RETURNED",old.version()+1,comment.trim(),p.name(),old.lines()),"RETURN",p);}
 private Request owned(String id,SessionPrincipal p,Integer version){actor(p);var old=load(id,true);if(!p.userId().equals(old.applicantId()))throw new BusinessException("MATERIAL_INTAKE_FORBIDDEN","只有申请人可以编辑申请");checkVersion(old,version);return old;}
 private void pending(Request r,Integer v){checkVersion(r,v);if(!"PENDING".equals(r.status()))throw invalid("申请不在待审核状态");}
 private void checkVersion(Request r,Integer v){if(v==null||v!=r.version())throw invalid("申请已更新，请刷新后重试");}
 private List<Line> snapshot(Write w,SessionPrincipal p){
  if(w==null||w.lines()==null||w.lines().isEmpty()||w.lines().size()>100)throw invalid("请选择1至100项新增物料");
  var current=source.loadEditableSource(w.formId(),w.trialId(),p);if(current.version()!=w.sourceVersion())throw invalid("来源方案已修改，请先保存并重新整理");
  var result=new ArrayList<Line>();var ids=new HashSet<String>();var codes=new HashSet<String>();
  for(var proposed:w.lines()){
   if(proposed.temporaryMaterialId()==null||!ids.add(proposed.temporaryMaterialId()))throw invalid("临时物料身份重复或缺失");
   if(proposed.code()==null||proposed.code().isBlank()||proposed.code().trim().length()>100)throw invalid("请补充有效物料编码");
   var code=proposed.code().trim().toUpperCase(Locale.ROOT);if(!codes.add(code))throw invalid("同一批申请编码不能重复");
   var rows=current.plan().majorProcesses().stream().flatMap(m->m.steps().stream()).flatMap(s->s.materials().stream()).filter(m->"EXTERNAL".equals(m.sourceType())&&m.catalogMaterialId()==null&&proposed.temporaryMaterialId().equals(m.temporaryMaterialId())).toList();
   if(rows.isEmpty())throw invalid("临时物料已删除或已经关联");var first=rows.get(0);
   if(first.materialName()==null||first.materialName().isBlank()||first.materialName().length()>200||first.temporaryClassificationId()==null)throw invalid("请填写临时物料名称与分类");
   boolean packaging="PACKAGING".equals(first.materialRole());
   if(packaging!= "PACKAGING".equals(classifications.requireAssignable(first.temporaryClassificationId()).businessType()))throw invalid("物料类型与分类不一致");
   if(packaging&&!List.of("个","张","只","袋","盒","箱","米").contains(first.quantityUnit()==null?"":first.quantityUnit()))throw invalid("请选择包装单位");
   if(first.temporarySpecification()!=null&&first.temporarySpecification().length()>300)throw invalid("规格不能超过300个字符");
   if(rows.stream().anyMatch(m->!Objects.equals(m.materialName(),first.materialName())||!Objects.equals(m.temporaryClassificationId(),first.temporaryClassificationId())||!Objects.equals(m.temporarySpecification(),first.temporarySpecification())||("PACKAGING".equals(m.materialRole())!=packaging)||!Objects.equals(m.quantityUnit(),first.quantityUnit())))throw invalid("同一临时物料的名称、分类、规格或单位不一致");
   if(packaging&&rows.stream().anyMatch(m->m.quantity()==null||m.quantity().signum()<0||m.weightKg()!=null))throw invalid("请完善包装数量");
   result.add(new Line(first.temporaryMaterialId(),code,first.materialName().trim(),first.temporaryClassificationId(),first.temporarySpecification(),packaging?null:rows.stream().map(m->m.weightKg()==null?BigDecimal.ZERO:m.weightKg()).reduce(BigDecimal.ZERO,BigDecimal::add),rows.stream().map(m->m.id()).toList(),null,packaging?rows.stream().map(m->m.quantity()).reduce(BigDecimal.ZERO,BigDecimal::add):null,packaging?first.quantityUnit():null,packaging?"PACKAGING":null));
  }return result;
 }
 private Request save(Request r,String action,SessionPrincipal p){db.update("update material_intake_request set payload_json=?,updated_at=current_timestamp where id=?",encode(r),r.id());record(r,action,p);return r;}
 private void record(Request r,String action,SessionPrincipal p){audit.record("MATERIAL_INTAKE",r.id(),action,p.name(),p.userId(),"status="+r.status()+";version="+r.version()+";comment="+r.comment());}
 private String encode(Request r){try{return mapper.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException(e);}}
 private Request decode(String json){try{return mapper.readValue(json,Request.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
