package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.model.PricingBasisDraft;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class PricingBasisServiceTest {
    JdbcTemplate db() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table pricing_file(id varchar primary key, status varchar)");
        jdbc.update("insert into pricing_file values('P','DRAFT_PACKAGING')");
        jdbc.execute("create table pricing_basis_snapshot(pricing_file_id varchar primary key, draft_json clob not null, version_no integer not null, confirmed_by varchar, confirmed_at varchar)");
        return jdbc;
    }
    static PricingBasisDraft valid() {
        var d = PricingBasisCalculatorTest.draft(PricingBasisCalculatorTest.material("AFTER_PROCESS","100","100","0.95"),"89");
        return new PricingBasisDraft(1,d.sourceRevisionId(),new PricingBasisDraft.Header("公司","香卤大肠头","500g/袋，20袋/箱","负责人","编写人","酱卤肉制品","DOC-1","A/0","2026-09-27"),d.materials(),d.mainOutputKg(),d.mainMaterialIds(),d.mainScopeBasis(),d.finishedNetKg(),d.unitWeightKg(),20,178,List.of(new PricingBasisDraft.Packaging("bag","B","包装袋","003",new java.math.BigDecimal("178"),"个","500g/袋",null,"0.99",null)));
    }
    @Test void roundTripsAndEditsInvalidateConfirmationWithVersionConflict() {
        var service = new PricingBasisService(db(),new ObjectMapper(),new PricingBasisCalculator());
        var d=valid();
        assertThat(service.load("P",d).version()).isZero();
        var saved=service.save("P",0,d,d);
        assertThat(service.load("P",d).draft()).isEqualTo(d);
        var confirmed=service.confirm("P",saved.version(),d,"研发甲");
        assertThat(confirmed.confirmedBy()).isEqualTo("研发甲");
        assertThatThrownBy(()->service.save("P",saved.version(),d,d)).hasMessageContaining("更新");
        assertThat(service.save("P",confirmed.version(),d,d).confirmedBy()).isNull();
    }
    @Test void changedSourceTamperedIdentityAndArchivedWritesAreRejected() {
        var jdbc=db(); var service=new PricingBasisService(jdbc,new ObjectMapper(),new PricingBasisCalculator());
        var d=valid(); service.save("P",0,d,d);
        var changed=new PricingBasisDraft(1,"new-revision",d.header(),d.materials(),d.mainOutputKg(),d.mainMaterialIds(),d.mainScopeBasis(),d.finishedNetKg(),d.unitWeightKg(),20,178,d.packaging());
        assertThat(service.load("P",changed).stale()).isTrue();
        assertThatThrownBy(()->service.confirm("P",1,changed,"研发甲")).hasMessageContaining("来源");
        var omitted=new PricingBasisDraft(1,d.sourceRevisionId(),d.header(),List.of(),d.mainOutputKg(),d.mainMaterialIds(),d.mainScopeBasis(),d.finishedNetKg(),d.unitWeightKg(),20,178,d.packaging());
        assertThatThrownBy(()->service.save("P",1,omitted,d)).hasMessageContaining("来源物料");
        jdbc.update("update pricing_file set status='PENDING_PRICING_REVIEW'");
        assertThatThrownBy(()->service.save("P",1,d,d)).hasMessageContaining("归档");
    }
}
