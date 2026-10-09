package com.lhr.rnd.service;
import com.lhr.rnd.model.MaterialCatalogItem;
import com.lhr.rnd.api.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialReferenceServiceTest {
 @Test void packagingQuantitySurvivesCatalogResolutionAndTrialCopy() throws Exception {
  when(catalog.find("BAG1")).thenReturn(new MaterialCatalogItem("BAG1","BAG","袋子","PACKAGING","500g","个","ACTIVE",0));
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  var material=mapper.readValue("""
   {"id":"bag","sequence":1,"materialRole":"PACKAGING","materialName":"伪造","sourceType":"EXTERNAL",
   "catalogMaterialId":"BAG1","quantity":277,"quantityUnit":"个"}
   """,com.lhr.rnd.model.ProcessPlan.StepMaterial.class);
  var resolved=service.resolveMaterial(material,null);
  assertThat(resolved.materialName()).isEqualTo("袋子");
  assertThat(mapper.valueToTree(resolved).path("quantity").decimalValue()).isEqualByComparingTo("277");
  assertThat(mapper.valueToTree(resolved).path("quantityUnit").asText()).isEqualTo("个");
  assertThat(resolved.weightKg()).isNull();
  var step=new com.lhr.rnd.model.ProcessPlan.MinorStep("s",1,"PACK","包装","NORMAL",null,null,null,null,null,null,null,null,java.util.List.of(resolved));
  var major=new com.lhr.rnd.model.ProcessPlan.MajorProcess("major",1,"PACK","包装",null,"PRIMARY_INPUT",null,java.util.List.of(step),java.util.List.of(),java.util.List.of(),null);
  var plan=new com.lhr.rnd.model.ProcessPlan("p","f",1,"DRAFT",java.util.List.of(major),null,false);
  var copy=new com.lhr.rnd.domain.TrialSchemeCopyService().copy(plan,com.lhr.rnd.model.TrialScheme.PlannedData.empty(),true,java.util.Map.of()).plan();
  var copied=mapper.valueToTree(copy.majorProcesses().get(0).steps().get(0).materials().get(0));
  assertThat(copied.path("quantity").decimalValue()).isEqualByComparingTo("277");
  assertThat(copied.path("quantityUnit").asText()).isEqualTo("个");
  assertThat(new com.lhr.rnd.domain.ProcessRecipeService().aggregate(copy)).isEmpty();
 }
 @Test void packagingCannotReferenceFoodIntermediate() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var material=mapper.readValue("{\"materialRole\":\"PACKAGING\",\"sourceType\":\"STEP_OUTPUT\",\"sourceStepOutputId\":\"food\"}",com.lhr.rnd.model.ProcessPlan.StepMaterial.class);
  assertThatThrownBy(()->service.resolveMaterial(material,null)).isInstanceOf(BusinessException.class);
 }
 @Test void intermediateClearsExternalIdentityButKeepsFlowAndQuantity(){
  var m=new com.lhr.rnd.model.ProcessPlan.StepMaterial("m",1,"PRIMARY","BEEF","牛肉","SOLID",java.math.BigDecimal.TEN,"old-formula",null,
   "STEP_OUTPUT","out",null,null,"MC1","旧规格","kg");
  var resolved=service.resolveMaterial(m,null);
  assertThat(resolved.materialCode()).isNull();
  assertThat(resolved.formulaMaterialId()).isNull();
  assertThat(resolved.catalogMaterialId()).isNull();
  assertThat(resolved.sourceStepOutputId()).isEqualTo("out");
  assertThat(resolved.materialName()).isEqualTo("牛肉");
  assertThat(resolved.weightKg()).isEqualByComparingTo("10");
 }
 @Test void deletingFirstLegacyRowRetainsRemainingSnapshotByIdentity() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  var first=mapper.readValue("{\"id\":\"row-a\",\"sequence\":1,\"catalogMaterialId\":\"MC1\",\"materialName\":\"最早快照\"}",com.lhr.rnd.model.ExperimentMaterial.class);
  var second=mapper.readValue("{\"id\":\"row-b\",\"sequence\":2,\"catalogMaterialId\":\"MC1\",\"materialName\":\"保留快照\"}",com.lhr.rnd.model.ExperimentMaterial.class);
  var incoming=mapper.readValue("{\"id\":\"row-b\",\"sequence\":1,\"catalogMaterialId\":\"MC1\",\"materialName\":\"客户端\"}",com.lhr.rnd.model.ExperimentMaterial.class);
  assertThat(service.resolveLegacy(java.util.List.of(incoming),java.util.List.of(first,second)).get(0).materialName()).isEqualTo("保留快照");
  verifyNoInteractions(catalog);
 }
 @Test void intermediateDoesNotRequireCatalog(){
  var m=new com.lhr.rnd.model.ProcessPlan.StepMaterial("m",1,"PRIMARY",null,"切块牛肉","SOLID",java.math.BigDecimal.TEN,null,null,
   "STEP_OUTPUT","out",null,null,"MC1","错误规格","kg");
  var resolved=service.resolveMaterial(m,null);
  assertThat(resolved.catalogMaterialId()).isNull();
  assertThat(resolved.sourceStepOutputId()).isEqualTo("out");
 }
 @Test void trialCopyKeepsReferenceAndSnapshot() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var material=mapper.readValue("""
   {"id":"m1","sequence":1,"materialRole":"PRIMARY","materialCode":"BEEF","materialName":"旧牛肉","weightKg":10,
    "sourceType":"EXTERNAL","catalogMaterialId":"MC1","catalogSpecification":"旧规格","catalogUnit":"kg"}
   """,com.lhr.rnd.model.ProcessPlan.StepMaterial.class);
  var step=new com.lhr.rnd.model.ProcessPlan.MinorStep("s",1,"CUT","切块","NORMAL",null,null,null,null,null,null,null,null,java.util.List.of(material));
  var major=new com.lhr.rnd.model.ProcessPlan.MajorProcess("major",1,"PREP","准备",null,"PRIMARY_INPUT",null,java.util.List.of(step),java.util.List.of(),java.util.List.of(),null);
  var plan=new com.lhr.rnd.model.ProcessPlan("p","f",1,"DRAFT",java.util.List.of(major),null,false);
  var copy=new com.lhr.rnd.domain.TrialSchemeCopyService().copy(plan,com.lhr.rnd.model.TrialScheme.PlannedData.empty(),true,java.util.Map.of()).plan();
  var copied=mapper.valueToTree(copy.majorProcesses().get(0).steps().get(0).materials().get(0));
  assertThat(copied.path("catalogMaterialId").asText()).isEqualTo("MC1");
  assertThat(copied.path("catalogSpecification").asText()).isEqualTo("旧规格");
  assertThat(copied.path("materialName").asText()).isEqualTo("旧牛肉");
 }
 MaterialCatalogService catalog=mock(MaterialCatalogService.class);
 MaterialReferenceService service=new MaterialReferenceService(catalog);
 MaterialReferenceService.Reference ref(String name){return new MaterialReferenceService.Reference("MC1","FAKE",name,"fake","L");}
 void active(String name){when(catalog.find("MC1")).thenReturn(new MaterialCatalogItem("MC1","BEEF",name,"RAW","500g","kg","ACTIVE",0));}
 @Test void forgedIdentityIsReplacedByCatalogSnapshot(){active("牛肉");var r=service.resolveForSave(ref("伪造"),null);assertThat(r.code()).isEqualTo("BEEF");assertThat(r.name()).isEqualTo("牛肉");assertThat(r.unit()).isEqualTo("kg");}
 @Test void renameDoesNotRewriteSavedSnapshot(){active("牛肉");var old=service.resolveForSave(ref("x"),null);active("牛肉新版");assertThat(service.resolveForSave(ref("伪造"),old)).isEqualTo(old);}
 @Test void inactiveExistingDraftCanSaveButCannotSubmit(){active("牛肉");var old=service.resolveForSave(ref("x"),null);when(catalog.find("MC1")).thenReturn(new MaterialCatalogItem("MC1","BEEF","牛肉","RAW","500g","kg","INACTIVE",1));assertThat(service.resolveForSave(old,old)).isEqualTo(old);assertThatThrownBy(()->service.requireActiveReference(old)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.resolveForSave(old,null)).isInstanceOf(BusinessException.class);}
 @Test void legacyUnlinkedDraftCanSaveButNewSubmissionFails(){var legacy=new MaterialReferenceService.Reference(null,"OLD","旧牛肉",null,null);assertThat(service.resolveForSave(legacy,null)).isEqualTo(legacy);assertThatThrownBy(()->service.requireActiveReference(legacy)).isInstanceOf(BusinessException.class);}
 @Test void packagingCannotBecomeIngredient(){when(catalog.find("MC1")).thenReturn(new MaterialCatalogItem("MC1","BAG","袋子","PACKAGING","","个","ACTIVE",0));assertThatThrownBy(()->service.resolveForSave(ref("x"),null)).isInstanceOf(BusinessException.class);}
}
