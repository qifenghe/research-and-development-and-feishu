package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.MaterialClassification;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;

@Service
public class MaterialClassificationService {
 private final JdbcTemplate db; private final AuditLogService audit;
 public MaterialClassificationService(JdbcTemplate db,AuditLogService audit){this.db=db;this.audit=audit;}
 public record Write(String parentId,String name,String businessType,Integer sortOrder,Integer expectedVersion){}
 private final RowMapper<MaterialClassification> mapper=(r,n)->new MaterialClassification(r.getString("id"),r.getString("parent_id"),r.getString("name"),r.getString("business_type"),r.getInt("sort_order"),r.getString("status"),r.getInt("version"));
 public List<MaterialClassification> list(){return db.query("select * from material_classification order by sort_order,name,id",mapper);}
 public MaterialClassification find(String id){return read(id,false);}
 private MaterialClassification read(String id,boolean lock){return db.query("select * from material_classification where id=?"+(lock?" for update":""),mapper,id).stream().findFirst().orElseThrow(()->invalid("分类不存在"));}
 /** All assignment/status transactions lock root then child, so parent disable cannot race assignment. */
 public MaterialClassification requireAssignable(String id){
  var initial=find(id);var root=read(initial.parentId()==null?id:initial.parentId(),true);
  var item=initial.parentId()==null?root:read(id,true);
  if(!"ACTIVE".equals(root.status())||!"ACTIVE".equals(item.status()))throw invalid("分类或其上级已停用");return item;
 }
 @Transactional public MaterialClassification create(Write w,SessionPrincipal actor){
  admin(actor);validate(w);String parent=blank(w.parentId())?null:w.parentId();String type=w.businessType();
  if(parent!=null){var p=requireAssignable(parent);if(p.parentId()!=null)throw invalid("最多支持两级分类");if(type!=null&&!type.equals(p.businessType()))throw invalid("下级分类业务类型须与上级一致");type=p.businessType();}
  if(type==null||!List.of("RAW","AUXILIARY","PROCESS_WATER","PACKAGING").contains(type))throw invalid("请选择业务类型");
  var id="CLS-"+UUID.randomUUID();
  try{db.update("insert into material_classification(id,parent_id,parent_scope,name,name_key,business_type,sort_order) values(?,?,?,?,?,?,?)",id,parent,parent==null?"ROOT":parent,w.name().trim(),key(w.name()),type,w.sortOrder()==null?0:w.sortOrder());}
  catch(DuplicateKeyException e){throw duplicate();}var result=find(id);record(actor,id,"CREATE",null,result);return result;
 }
 @Transactional public MaterialClassification update(String id,Write w,SessionPrincipal actor){
  admin(actor);validate(w);var old=find(id);
  if(w.parentId()!=null&&!Objects.equals(w.parentId(),old.parentId())||w.businessType()!=null&&!w.businessType().equals(old.businessType()))throw invalid("分类父级及业务类型不可修改");
  if(w.expectedVersion()==null)throw invalid("缺少版本号");
  try{conflict(db.update("update material_classification set name=?,name_key=?,sort_order=?,version=version+1,updated_at=current_timestamp where id=? and version=?",w.name().trim(),key(w.name()),w.sortOrder()==null?old.sortOrder():w.sortOrder(),id,w.expectedVersion()));}
  catch(DuplicateKeyException e){throw duplicate();}var result=find(id);record(actor,id,"UPDATE",old,result);return result;
 }
 @Transactional public MaterialClassification status(String id,String status,Integer version,SessionPrincipal actor){
  admin(actor);if(version==null||status==null||!List.of("ACTIVE","INACTIVE").contains(status))throw invalid("状态或版本号无效");
  var initial=find(id);if(initial.parentId()!=null)read(initial.parentId(),true);var old=read(id,true);
  conflict(db.update("update material_classification set status=?,version=version+1,updated_at=current_timestamp where id=? and version=?",status,id,version));
  var result=find(id);record(actor,id,"STATUS",old,result);return result;
 }
 private void admin(SessionPrincipal actor){if(actor==null||!"SYSTEM_ADMIN".equals(actor.role()))throw new BusinessException("CLASSIFICATION_FORBIDDEN","只有超级管理员可维护分类");}
 private void validate(Write w){if(w==null||blank(w.name())||w.name().trim().length()>100||w.sortOrder()!=null&&w.sortOrder()<0)throw invalid("分类名称必填且不超过100字，排序不能为负数");}
 private void record(SessionPrincipal p,String id,String action,Object before,Object after){audit.record("MATERIAL_CLASSIFICATION",id,action,p.name(),p.userId(),"before="+before+"; after="+after);}
 private String key(String name){return name.trim().toUpperCase(Locale.ROOT);}
 private boolean blank(String v){return v==null||v.isBlank();}
 private void conflict(int count){if(count!=1)throw new BusinessException("CLASSIFICATION_VERSION_CONFLICT","分类已修改，请刷新后重试");}
 private BusinessException duplicate(){return new BusinessException("CLASSIFICATION_NAME_CONFLICT","同级分类名称已存在");}
 private BusinessException invalid(String message){return new BusinessException("CLASSIFICATION_INVALID",message);}
}
