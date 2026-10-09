package com.lhr.rnd.service;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.SampleRequirementValidation;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.model.SampleRequest;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestIntakeService {
    private final SampleWorkflowService workflow;
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final RolePermissionService permissions;

    public RequestIntakeService(SampleWorkflowService workflow, JdbcTemplate jdbc, EntityManager entities, RolePermissionService permissions) {
        this.workflow=workflow; this.jdbc=jdbc; this.entities=entities; this.permissions=permissions;
    }

    @Transactional
    public RequestView create(SampleWorkflowService.CreateSampleRequestCommand command, SessionPrincipal actor) {
        requireWriter(actor);
        var request=workflow.createRequest(command);
        entities.flush();
        jdbc.update("insert into sample_request_intake(request_id,recorder_user_id,recorder_name,version_no) values(?,?,?,0)",request.id(),actor.userId(),actor.name());
        record(request.id(),0,"SUBMITTED",actor,null);
        return view(request,actor);
    }

    @Transactional
    public RequestView reject(String id, Integer expectedVersion, String comment, SessionPrincipal actor) {
        requireReviewer(actor);
        var normalized=SampleRequirementValidation.text(comment,"驳回批语",true,2000);
        int version=lock(id,expectedVersion);
        var current=workflow.requestDetail(id);
        requireStatus(current,SampleStatus.PENDING_REVIEW);
        var request=workflow.reviseRequest(id,null,SampleStatus.REJECTED);
        advance(id,version,"REJECTED",actor,normalized);
        return view(request,actor);
    }

    @Transactional
    public RequestView resubmit(String id, Integer expectedVersion, SampleWorkflowService.CreateSampleRequestCommand command, SessionPrincipal actor) {
        requireWriter(actor);
        int version=lock(id,expectedVersion);
        requireStatus(workflow.requestDetail(id),SampleStatus.REJECTED);
        var request=workflow.reviseRequest(id,command,SampleStatus.PENDING_REVIEW);
        advance(id,version,"RESUBMITTED",actor,null);
        return view(request,actor);
    }

    @Transactional
    public ApproveSampleRequestResult approve(String id,Integer expectedVersion,SessionPrincipal actor) {
        requireReviewer(actor);
        int version=lock(id,expectedVersion);
        requireStatus(workflow.requestDetail(id),SampleStatus.PENDING_REVIEW);
        var result=workflow.approveRequest(id,actor.name());
        entities.flush();
        advance(id,version,"APPROVED",actor,null);
        return result;
    }

    public RequestView view(SampleRequest request, SessionPrincipal actor) {
        var metadata=jdbc.query("select recorder_user_id,recorder_name,version_no from sample_request_intake where request_id=?",
                (rs,row)->new Metadata(rs.getString(1),rs.getString(2),rs.getInt(3)),request.id());
        var meta=metadata.isEmpty()?new Metadata(null,null,0):metadata.get(0);
        var history=jdbc.query("select version_no,action,actor_user_id,actor_name,comment,acted_at from sample_request_review where request_id=? order by version_no",
                (rs,row)->new Review(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toLocalDateTime()),request.id());
        boolean canResubmit=actor!=null && writerRole(actor.role()) && permissions.hasPermission(actor.role(),"PUT","/api/v1/sample-requests/"+request.id()+"/resubmit");
        return new RequestView(request,meta.recorderUserId(),meta.recorderName(),meta.versionNo(),history,canResubmit);
    }

    private int lock(String id,Integer expected) {
        // Lock the parent even for legacy/direct-service requests without intake metadata.
        var rows=jdbc.queryForList("select id from sample_request where id=? for update",String.class,id);
        if(rows.isEmpty()) throw new BusinessException("SAMPLE_REQUEST_NOT_FOUND","样品需求不存在");
        var versions=jdbc.queryForList("select version_no from sample_request_intake where request_id=?",Integer.class,id);
        int version=versions.isEmpty()?0:versions.get(0);
        if((expected==null && version>0) || (expected!=null && expected!=version)) throw new BusinessException("REQUEST_VERSION_CONFLICT","需求已更新，请刷新后重新操作");
        if(versions.isEmpty()) jdbc.update("insert into sample_request_intake(request_id,version_no) values(?,0)",id);
        return version;
    }
    private void advance(String id,int version,String action,SessionPrincipal actor,String comment) {
        entities.flush();
        jdbc.update("update sample_request_intake set version_no=? where request_id=?",version+1,id);
        record(id,version+1,action,actor,comment);
    }
    private void record(String id,int version,String action,SessionPrincipal actor,String comment) {
        jdbc.update("insert into sample_request_review(request_id,version_no,action,actor_user_id,actor_name,comment,acted_at) values(?,?,?,?,?,?,?)",
                id,version,action,actor.userId(),actor.name(),comment,LocalDateTime.now());
    }
    private void requireStatus(SampleRequest request,SampleStatus expected) {
        if(request.status()!=expected) throw new BusinessException("SAMPLE_REQUEST_STATUS_ILLEGAL","当前需求状态不可执行此操作，请刷新查看");
    }
    private static boolean writerRole(String role) {
        return List.of("RND_ASSISTANT","RND_DIRECTOR","ADMIN","SYSTEM_ADMIN").contains(role);
    }
    private void requireWriter(SessionPrincipal actor) {
        requireActor(actor);
        if(!writerRole(actor.role())) throw new BusinessException("REQUEST_WRITE_FORBIDDEN","当前角色不能录入或修改需求");
    }
    private void requireReviewer(SessionPrincipal actor) {
        requireActor(actor);
        if(!List.of("RND_DIRECTOR","ADMIN","SYSTEM_ADMIN").contains(actor.role())) throw new BusinessException("REQUEST_REVIEW_FORBIDDEN","只有研发总监或管理员可审核需求");
    }
    private void requireActor(SessionPrincipal actor) {
        if(actor==null) throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","请登录后操作需求");
    }
    private record Metadata(String recorderUserId,String recorderName,int versionNo) {}
    public record Review(int versionNo,String action,String actorUserId,String actorName,String comment,LocalDateTime actedAt) {}
    public record RequestView(@JsonUnwrapped SampleRequest request,String recorderUserId,String recorderName,int reviewVersion,List<Review> reviewHistory,boolean canResubmit) {}
}
