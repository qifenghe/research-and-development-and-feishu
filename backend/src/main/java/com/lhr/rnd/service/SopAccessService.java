package com.lhr.rnd.service;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessRevision;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class SopAccessService {
 private final RolePermissionService permissions;private final ProcessRevisionService revisions;
 public SopAccessService(RolePermissionService permissions,ProcessRevisionService revisions){this.permissions=permissions;this.revisions=revisions;}
 public ProcessRevision require(String formId,String revisionId,String method,String suffix,SessionPrincipal actor){
  if(actor==null||actor.userId()==null||actor.name()==null)throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","请先登录");
  String path="/api/v1/sops/"+formId+"/revisions/"+revisionId+suffix;
  if(!List.of("RND_ASSISTANT","RND_DIRECTOR").contains(actor.role())||!permissions.hasPermission(actor.role(),method,path))throw new BusinessException("SOP_FORBIDDEN","当前身份无权操作正式SOP");
  return revisions.find(formId,revisionId);
 }
}
