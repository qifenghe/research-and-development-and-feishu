package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.*;
import com.lhr.rnd.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialIntakePackagingTest {
 @Test void packagingRequestKeepsQuantityAndSeparatesSpecifications() throws Exception {
  var mapper=new ObjectMapper();
  var plan=mapper.readValue("""
   {"majorProcesses":[{"steps":[{"materials":[
   {"id":"a","sourceType":"EXTERNAL","materialRole":"PACKAGING","temporaryMaterialId":"tmp-a","temporaryClassificationId":"pack","temporarySpecification":"小袋","materialName":"袋","quantity":277,"quantityUnit":"个"},
   {"id":"b","sourceType":"EXTERNAL","materialRole":"PACKAGING","temporaryMaterialId":"tmp-b","temporaryClassificationId":"pack","temporarySpecification":"大袋","materialName":"袋","quantity":16,"quantityUnit":"个"}
   ]}]}]}
   """,ProcessPlan.class);
  var source=mock(MaterialIntakeSourceService.class);
  var actor=new SessionPrincipal("u","u","内勤",null,"RND_ASSISTANT",null);
  when(source.loadEditableSource("f","t",actor)).thenReturn(new MaterialIntakeSourceService.Source("f","t",1,plan,null));
  var cls=mock(MaterialClassificationService.class);
  when(cls.requireAssignable("pack")).thenReturn(new MaterialClassification("pack",null,"包材","PACKAGING",0,"ACTIVE",0));
  var service=new MaterialIntakeService(mock(JdbcTemplate.class),mapper,source,mock(MaterialCatalogService.class),mock(AuditLogService.class),cls);
  var request=service.create(new MaterialIntakeService.Write("f","t",1,null,List.of(new MaterialIntakeService.ProposedLine("tmp-a","B1"),new MaterialIntakeService.ProposedLine("tmp-b","B2"))),actor);
  var lines=mapper.valueToTree(request).path("lines");
  assertThat(lines.size()).isEqualTo(2);
  assertThat(lines.get(0).path("quantity").decimalValue()).isEqualByComparingTo("277");
  assertThat(lines.get(1).path("quantity").decimalValue()).isEqualByComparingTo("16");
  assertThat(lines.get(0).path("quantityUnit").asText()).isEqualTo("个");
  assertThat(lines.get(0).path("totalWeightKg").isNull()).isTrue();
 }
}
