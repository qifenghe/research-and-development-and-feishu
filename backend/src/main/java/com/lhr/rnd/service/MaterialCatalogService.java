package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.MaterialCatalogItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;

@Service
public class MaterialCatalogService {
 private final JdbcTemplate db; private final AuditLogService audit;
 public MaterialCatalogService(JdbcTemplate db,AuditLogService audit){this.db=db;this.audit=audit;}
 public record Write(String code,String name,String category,String specification,String unit,Integer expectedVersion,String classificationId){
  public Write(String code,String name,String category,String specification,String unit,Integer expectedVersion){this(code,name,category,specification,unit,expectedVersion,null);}
 }
 public record Page(List<MaterialCatalogItem> items,long total,int page,int size){}
 private final RowMapper<MaterialCatalogItem> mapper=(r,n)->new MaterialCatalogItem(r.getString("id"),r.getString("code"),r.getString("name"),r.getString("category"),r.getString("specification"),r.getString("unit"),r.getString("status"),r.getInt("version"),r.getString("classification_id"));
 public Page list(String query,String category,String status,int page,int size){
  return list(query,category,status,page,size,null);
 }
 public Page list(String query,String category,String status,int page,int size,String classificationId){
  if(page<1||size<1||size>100)throw invalid("页码或每页条数无效");
  var args=new ArrayList<Object>();var sql=" from material_catalog where 1=1";
  if(query!=null&&!query.isBlank()){sql+=" and (upper(code) like ? escape '!' or upper(name) like ? escape '!')";var q="%"+query.trim().toUpperCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(q);args.add(q);}
  if(category!=null&&!category.isBlank()){sql+=" and category=?";args.add(category);}
  if(classificationId!=null&&!classificationId.isBlank()){sql+=" and (classification_id=? or classification_id in (select id from material_classification where parent_id=?))";args.add(classificationId);args.add(classificationId);}
  if(status!=null&&!status.isBlank()){if(!List.of("ACTIVE","INACTIVE").contains(status))throw invalid("状态无效");sql+=" and status=?";args.add(status);}
  var total=db.queryForObject("select count(*)"+sql,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
  return new Page(db.query("select *"+sql+" order by code limit ? offset ?",mapper,args.toArray()),total,page,size);
 }
 public MaterialCatalogItem find(String id){return db.query("select * from material_catalog where id=?",mapper,id).stream().findFirst().orElseThrow(()->invalid("物料不存在"));}
 @Transactional public MaterialCatalogItem create(Write w,SessionPrincipal actor){
  w=classify(w,null);validate(w);var id="MC-"+UUID.randomUUID();
  try{db.update("insert into material_catalog(id,code,name,category,specification,unit,classification_id,status,version,created_at,updated_at) values (?,?,?,?,?,?,?,'ACTIVE',0,current_timestamp,current_timestamp)",id,code(w.code()),w.name().trim(),w.category(),w.specification(),w.unit(),w.classificationId());}
  catch(DuplicateKeyException ex){throw new BusinessException("MATERIAL_CODE_CONFLICT","物料编码已存在");}
  var result=find(id);record(actor,id,"CREATE",null,result);return result;
 }
 @Transactional public MaterialCatalogItem update(String id,Write w,SessionPrincipal actor){
  var old=find(id);w=classify(w,old);validate(w);if(!old.code().equals(code(w.code())))throw invalid("创建后不可修改物料编码");
  if(w.expectedVersion()==null)throw invalid("缺少版本号");
  int count=db.update("update material_catalog set name=?,category=?,specification=?,unit=?,classification_id=?,version=version+1,updated_at=current_timestamp where id=? and version=?",w.name().trim(),w.category(),w.specification(),w.unit(),w.classificationId(),id,w.expectedVersion());
  conflict(count);var result=find(id);record(actor,id,"UPDATE",old,result);return result;
 }
 @Transactional public MaterialCatalogItem status(String id,String status,Integer version,SessionPrincipal actor){
  if(status==null||!List.of("ACTIVE","INACTIVE").contains(status)||version==null)throw invalid("状态或版本号无效");
  var old=find(id);conflict(db.update("update material_catalog set status=?,version=version+1,updated_at=current_timestamp where id=? and version=?",status,id,version));var result=find(id);record(actor,id,"STATUS",old,result);return result;
 }
 private void record(SessionPrincipal p,String id,String action,Object before,Object after){audit.record("MATERIAL",id,action,p.name(),p.userId(),"before="+before+"; after="+after);}
 private void conflict(int n){if(n!=1)throw new BusinessException("MATERIAL_VERSION_CONFLICT","物料已被修改，请刷新后重试");}
 private String code(String v){return v.trim().toUpperCase(Locale.ROOT);}
 private Write classify(Write w,MaterialCatalogItem old){
  if(w==null)throw invalid("物料不能为空");
  var classificationId=w.classificationId();
  if(classificationId==null||classificationId.isBlank())classificationId=old!=null&&old.classificationId()!=null?old.classificationId():"CAT-"+w.category();
  var classifications=new MaterialClassificationService(db,audit);
  var classification=old!=null&&Objects.equals(classificationId,old.classificationId())?classifications.find(classificationId):classifications.requireAssignable(classificationId);
  if(w.category()!=null&&!w.category().equals(classification.businessType()))throw new BusinessException("CLASSIFICATION_INVALID","物料类型与分类不一致");
  return new Write(w.code(),w.name(),classification.businessType(),w.specification(),w.unit(),w.expectedVersion(),classificationId);
 }
 private void validate(Write w){if(w==null||w.code()==null||w.code().isBlank()||w.code().trim().length()>100||w.name()==null||w.name().isBlank()||w.name().trim().length()>200||w.category()==null||!List.of("RAW","AUXILIARY","PROCESS_WATER","PACKAGING").contains(w.category())||w.specification()!=null&&w.specification().length()>300)throw invalid("请填写有效编码、名称、类别和规格");
  if(w.unit()==null||("PACKAGING".equals(w.category())?!List.of("个","张","米","袋","盒","箱").contains(w.unit()):!"kg".equals(w.unit())))throw invalid("原辅料单位必须为kg，包装请选择计数单位");
 }
 private BusinessException invalid(String m){return new BusinessException("MATERIAL_INVALID",m);}
}
