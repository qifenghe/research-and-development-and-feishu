package com.lhr.rnd.service;
import org.junit.jupiter.api.Test;
import com.lhr.rnd.api.BusinessException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.*;

class SopAmendmentPublishTest extends SopAmendmentServiceTest {
 @Test void directorPublishesExactlyOnceAndPreservesLockedResearch() throws Exception {
  var r=seed();var original=plans.find(r.experimentFormId());
  db.update("update experiment_form set status='COMPLETED' where id=?",r.experimentFormId());
  var a=amendments.create(r.experimentFormId(),r.id(),"车间调整",assistant);
  a=amendments.save(a.id(),a.versionNo(),"车间调整",null,change(a.snapshot(),"instruction","沥水5分钟"),assistant);
  a=amendments.submit(a.id(),a.versionNo(),assistant);final var pending=a;
  assertThatThrownBy(()->amendments.approve(pending.id(),pending.versionNo(),true,assistant)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->amendments.approve(pending.id(),pending.versionNo(),false,director)).isInstanceOf(BusinessException.class);
  var result=amendments.approve(pending.id(),pending.versionNo(),true,director);
  assertThat(result.amendment().status()).isEqualTo("PUBLISHED");assertThat(result.revision().revisionNo()).isEqualTo(2);
  assertThat(result.revision().sourceRevisionId()).isEqualTo(r.id());
  assertThat(result.revision().snapshot().majorProcesses().get(0).steps().get(0).instruction()).isEqualTo("沥水5分钟");
  assertThat(result.revision().submittedBy()).isEqualTo("总监");
  assertThat(db.queryForObject("select compiled_by from experiment_process_revision where id=?",String.class,result.revision().id())).isEqualTo(assistant.name());
  assertThat(db.queryForObject("select reviewed_by from experiment_process_revision where id=?",String.class,result.revision().id())).isEqualTo(director.name());
  assertThat(db.queryForObject("select reviewed_at from experiment_process_revision where id=?",java.sql.Timestamp.class,result.revision().id())).isNotNull();
  assertThat(plans.find(r.experimentFormId())).isEqualTo(original);
  assertThatThrownBy(()->amendments.approve(pending.id(),pending.versionNo(),true,director)).isInstanceOf(BusinessException.class);
  assertThat(db.queryForObject("select count(*) from experiment_process_revision where experiment_form_id=?",Integer.class,r.experimentFormId())).isEqualTo(2);
 }
 @Test void returnedDraftRetainsCommentsAndResubmits() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"参数调整",assistant);
  a=amendments.save(a.id(),a.versionNo(),"参数调整",null,change(a.snapshot(),"instruction","沥水"),assistant);
  a=amendments.submit(a.id(),a.versionNo(),assistant);final var pending=a;
  assertThatThrownBy(()->amendments.returnForChanges(pending.id(),pending.versionNo()," ",director)).isInstanceOf(BusinessException.class);
  a=amendments.returnForChanges(a.id(),a.versionNo(),"补充沥水时间",director);
  assertThat(a.status()).isEqualTo("RETURNED");assertThat(a.history()).anyMatch(h->"补充沥水时间".equals(h.comment()));
  a=amendments.save(a.id(),a.versionNo(),"参数调整",null,change(a.snapshot(),"instruction","沥水5分钟"),assistant);
  assertThat(amendments.submit(a.id(),a.versionNo(),assistant).status()).isEqualTo("PENDING_REVIEW");
 }
 @Test void staleAndTamperedPendingBlocked() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"参数调整",assistant);
  a=amendments.save(a.id(),a.versionNo(),"参数调整",null,change(a.snapshot(),"instruction","沥水"),assistant);
  a=amendments.submit(a.id(),a.versionNo(),assistant);final var pending=a;
  db.update("update sop_amendment set submitted_hash='BAD' where id=?",pending.id());
  assertThatThrownBy(()->amendments.approve(pending.id(),pending.versionNo(),true,director)).isInstanceOf(BusinessException.class);
  db.update("update sop_amendment set submitted_hash=snapshot_hash where id=?",pending.id());
  var draft=revisions.createDraftFromRevision(r.experimentFormId(),r.id(),"研发调整");
  revisions.submit(r.experimentFormId(),new ProcessRevisionService.SubmitCommand(draft.versionNo(),true,"研发调整","研发"));
  assertThatThrownBy(()->amendments.approve(pending.id(),pending.versionNo(),true,director)).isInstanceOf(BusinessException.class);
  assertThat(amendments.get(pending.id(),assistant).status()).isEqualTo("PENDING_REVIEW");
 }
 @Test void concurrentApprovalOnlyCreatesOneRevision() throws Exception {
  var r=seed();var a=amendments.create(r.experimentFormId(),r.id(),"参数调整",assistant);
  a=amendments.save(a.id(),a.versionNo(),"参数调整",null,change(a.snapshot(),"instruction","沥水"),assistant);
  final var pending=amendments.submit(a.id(),a.versionNo(),assistant);var go=new CountDownLatch(1);
  java.util.function.Supplier<Boolean> run=()->{try{go.await();amendments.approve(pending.id(),pending.versionNo(),true,director);return true;}catch(BusinessException e){return false;}catch(Exception e){throw new RuntimeException(e);}};
  var one=CompletableFuture.supplyAsync(run);var two=CompletableFuture.supplyAsync(run);go.countDown();
  assertThat(java.util.List.of(one.join(),two.join())).containsExactlyInAnyOrder(true,false);
  assertThat(db.queryForObject("select count(*) from experiment_process_revision where experiment_form_id=?",Integer.class,r.experimentFormId())).isEqualTo(2);
 }
}
