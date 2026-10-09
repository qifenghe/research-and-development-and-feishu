package com.lhr.rnd.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @ActiveProfiles("test")
class MaterialIntakePackagingApprovalTest {
 @Autowired JdbcTemplate db; @Autowired ObjectMapper mapper; @Autowired MaterialCatalogService catalog;
 @Autowired MaterialClassificationService classifications; @Autowired AuditLogService audit;
 @Test void directorCreatesPackagingWithRequestedUnitAndRejectsMismatchedExistingUnit() throws Exception {
  var actor=new SessionPrincipal("intake-package","intake-package","总监",null,"RND_DIRECTOR",null);
  var cls=db.queryForList("select id from material_classification where business_type='PACKAGING' and status='ACTIVE'",String.class).get(0);
  var plan=mapper.readValue("""
   {"majorProcesses":[{"steps":[{"materials":[{"id":"bag","sourceType":"EXTERNAL","materialRole":"PACKAGING","temporaryMaterialId":"tmp","materialName":"新袋","quantity":277,"quantityUnit":"只","temporaryClassificationId":"%s"}]}]}]}
   """.formatted(cls),ProcessPlan.class);
  var source=mock(MaterialIntakeSourceService.class);
  when(source.loadEditableSource("f","t",actor)).thenReturn(new MaterialIntakeSourceService.Source("f","t",1,plan,null));
  var service=new MaterialIntakeService(db,mapper,source,catalog,audit,classifications);
  var w=new MaterialIntakeService.Write("f","t",1,null,List.of(new MaterialIntakeService.ProposedLine("tmp","PACK-"+UUID.randomUUID())));
  var draft=service.create(w,actor);var pending=service.submit(draft.id(),draft.version(),actor);
  var approved=service.approve(pending.id(),new MaterialIntakeService.Review(pending.version(),List.of(new MaterialIntakeService.Decision("tmp","CREATE",null)),"同意"),actor);
  assertThat(approved.lines().get(0).quantity()).isEqualByComparingTo("277");
  assertThat(catalog.find(approved.lines().get(0).catalogMaterialId()).unit()).isEqualTo("只");
  verify(source).applyApprovedReferences(any(),anyMap(),eq(actor));
  var different=catalog.create(new MaterialCatalogService.Write("OTHER-"+UUID.randomUUID(),"旧袋",null,null,"个",null,cls),actor);
  var second=service.create(new MaterialIntakeService.Write("f","t",1,null,List.of(new MaterialIntakeService.ProposedLine("tmp","LINK-"+UUID.randomUUID()))),actor);
  var secondPending=service.submit(second.id(),second.version(),actor);
  assertThatThrownBy(()->service.approve(secondPending.id(),new MaterialIntakeService.Review(secondPending.version(),List.of(new MaterialIntakeService.Decision("tmp","LINK_EXISTING",different.id())),null),actor)).hasMessageContaining("单位一致");
 }
}
