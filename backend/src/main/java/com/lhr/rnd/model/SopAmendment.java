package com.lhr.rnd.model;
import com.lhr.rnd.domain.SopAmendmentDiff.Difference;
import java.util.List;
public record SopAmendment(String id,String formId,String sourceRevisionId,String sourceHash,int versionNo,String status,
 String reason,String evidenceNote,String createdById,String createdBy,String createdAt,String updatedAt,
 String publishedRevisionId,ProcessPlan snapshot,List<Difference> differences,List<History> history){
 public record History(int versionNo,String action,String actorId,String actorName,String comment,String actedAt){}
}
