package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class StepParameterPersistenceTest {
    @Autowired ObjectMapper mapper;
    @Autowired ProcessPlanService service;
    @Autowired JdbcTemplate jdbc;

    private String seed() {
        var id = "PARAM-" + UUID.randomUUID().toString().substring(0, 8);
        var now = LocalDateTime.now();
        jdbc.update("insert into sample_request(id,sample_no,product_name,product_type,customer_name,specification,creator_name,status,created_at) values (?,?,?,?,?,?,?,?,?)", id,id,"参数测试","预制菜","软件测试","1kg","研发","PENDING_ASSIGNMENT",now);
        jdbc.update("insert into sample_project(id,request_id,sample_no,product_name,product_type,customer_name,specification,status,created_at) values (?,?,?,?,?,?,?,?,?)", id,id,id,"参数测试","预制菜","软件测试","1kg","ACTIVE",now);
        jdbc.update("insert into sample_version(id,project_id,sample_no,product_name,product_type,specification,version_no,version_number,version_code,created_at) values (?,?,?,?,?,?,?,?,?,?)", id,id,id,"参数测试","预制菜","1kg","1",1,"V1",now);
        jdbc.update("insert into rnd_task(id,project_id,version_id,sample_no,product_name,version_code,status,created_at) values (?,?,?,?,?,?,?,?)", id,id,id,id,"参数测试","V1","SAMPLING",now);
        jdbc.update("insert into experiment_form(id,task_id,project_id,version_id,sample_no,product_name,version_code,status,operator_name,saved_at) values (?,?,?,?,?,?,?,?,?,?)", id,id,id,id,id,"参数测试","V1","DRAFT","研发",now);
        return id;
    }
    private ProcessPlan request(String form, String parameters) throws Exception {
        return mapper.readValue("""
            {"experimentFormId":"%s","versionNo":0,"status":"DRAFT","majorProcesses":[
             {"sequence":1,"processName":"滚揉","yieldBasis":"NONE","inputs":[],"outputs":[],"steps":[
              {"sequence":1,"stepName":"滚揉","stepType":"NORMAL","parameter1Name":"旧温度","parameter1Value":"97","parameter1Unit":"℃",%s
               "materials":[{"sequence":1,"materialRole":"PRIMARY","materialName":"主料","materialState":"SOLID","weightKg":10,"sourceType":"EXTERNAL","additionTiming":"开始时加入","additionMethod":"分三次"}],"outputs":[],"controlPoints":[]}
             ]}]}
            """.formatted(form, parameters), ProcessPlan.class);
    }
    @Test void savesAllParametersAndAdditionDetailsInRelationalStorage() throws Exception {
        var form=seed();
        var plan=request(form, """
            "parameters":[
             {"key":"speed","name":"转速","valueType":"NUMBER","unit":"rpm","actualValue":"8","showInSummary":true},
             {"key":"vacuum","name":"压力","valueType":"NUMBER","unit":"MPa","actualValue":"-0.08","measurementBasis":"表压","showInSummary":true},
             {"key":"run","name":"运行时间","valueType":"NUMBER","unit":"min","actualValue":"20","showInSummary":false},
             {"key":"rest","name":"暂停时间","valueType":"NUMBER","unit":"min","actualValue":"5","showInSummary":false}],
            """);
        service.save(form,plan);
        var step=mapper.valueToTree(service.find(form)).path("majorProcesses").get(0).path("steps").get(0);
        assertThat(step.path("parameters").size()).isEqualTo(4);
        assertThat(step.path("parameters").get(1).path("actualValue").asText()).isEqualTo("-0.08");
        assertThat(step.path("parameters").get(3).path("actualValue").asText()).isEqualTo("5");
        assertThat(step.path("parameter1Value").isNull()).isTrue();
        assertThat(step.path("materials").get(0).path("additionTiming").asText()).isEqualTo("开始时加入");
        assertThat(step.path("materials").get(0).path("additionMethod").asText()).isEqualTo("分三次");
    }
    @Test void explicitEmptyListDoesNotReviveLegacyParameters() throws Exception {
        var form=seed(); service.save(form,request(form,"\"parameters\":[],"));
        var step=mapper.valueToTree(service.find(form)).path("majorProcesses").get(0).path("steps").get(0);
        assertThat(step.path("parameters").isArray()).isTrue();
        assertThat(step.path("parameters").size()).isZero();
        assertThat(step.path("parameter1Value").isNull()).isTrue();
    }
    @Test void rejectsInvalidNumericTypesDuplicateKeysAndInvertedBounds() throws Exception {
        for (String data : new String[]{
            "[{\"key\":\"x\",\"valueType\":\"NUMBER\",\"actualValue\":\"NaN\"}]",
            "[{\"key\":\"x\",\"valueType\":\"NUMBER\",\"lowerLimit\":\"8\",\"upperLimit\":\"2\"}]",
            "[{\"key\":\"x\",\"valueType\":\"OTHER\"}]",
            "[{\"key\":\"x\",\"valueType\":\"TEXT\"},{\"key\":\"x\",\"valueType\":\"TEXT\"}]"}) {
            var form=seed(); var plan=request(form,"\"parameters\":"+data+",");
            assertThatThrownBy(() -> service.save(form,plan)).isInstanceOf(com.lhr.rnd.api.BusinessException.class);
        }
    }
    @Test void oldSnapshotStillDeserializesAndRoundTrips() throws Exception {
        var form=seed(); service.save(form,request(form,""));
        assertThat(service.find(form).majorProcesses().get(0).steps().get(0).parameter1Value()).isEqualTo("97");
    }
}
