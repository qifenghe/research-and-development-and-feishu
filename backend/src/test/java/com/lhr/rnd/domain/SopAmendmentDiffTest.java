package com.lhr.rnd.domain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SopAmendmentDiffTest {
 final ObjectMapper mapper=new ObjectMapper();
 @Test void numericAndReferenceDifferences() throws Exception {
  var before=mapper.readTree("{\"majorProcesses\":[{\"id\":\"M\",\"sequence\":1,\"steps\":[{\"id\":\"S\",\"sequence\":1,\"parameters\":[{\"key\":\"rpm\",\"valueType\":\"NUMBER\",\"actualValue\":\"1\"}],\"outputs\":[{\"id\":\"O\",\"weightKg\":null}]}]}]}");
  var after=mapper.readTree(before.toString().replace("\"1\"","\"1.0\"").replace("\"weightKg\":null","\"weightKg\":0"));
  var differences=new SopAmendmentDiff().compare(before,after);
  assertThat(differences).hasSize(1);assertThat(differences.get(0).entityId()).isEqualTo("O");
  assertThat(differences.get(0).fieldPath()).endsWith("/weightKg");
 }
 @Test void reorderedEntitiesKeepIdentityAndDeletedFieldsRemainVisible() throws Exception {
  var before=mapper.readTree("{\"majorProcesses\":[{\"id\":\"M\",\"sequence\":1,\"steps\":[{\"id\":\"S1\",\"sequence\":1,\"instruction\":\"清洗\"},{\"id\":\"S2\",\"sequence\":2,\"instruction\":\"切块\"}]}]}");
  var after=mapper.readTree("{\"majorProcesses\":[{\"id\":\"M\",\"sequence\":1,\"steps\":[{\"id\":\"S2\",\"sequence\":1,\"instruction\":\"切块\"}]}]}");
  var differences=new SopAmendmentDiff().compare(before,after);
  assertThat(differences).anyMatch(d->d.type().equals("REMOVED")&&d.entityId().equals("S1")&&d.before().contains("清洗"));
  assertThat(differences).anyMatch(d->d.type().equals("MOVED")&&d.entityId().equals("S2"));
  assertThat(differences).noneMatch(d->d.type().equals("ADDED"));
 }
}
