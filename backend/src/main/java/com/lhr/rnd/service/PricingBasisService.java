package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.PricingBasisDraft;
import com.lhr.rnd.model.PricingBasisResult;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistence and confirmation only. Public workflow methods enforce session access before calling. */
@Service
public class PricingBasisService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PricingBasisCalculator calculator;
    public PricingBasisService(JdbcTemplate jdbc,ObjectMapper json,PricingBasisCalculator calculator) { this.jdbc=jdbc; this.json=json; this.calculator=calculator; }
    public record View(PricingBasisDraft draft,int version,PricingBasisResult calculation,String confirmedBy,String confirmedAt,boolean stale,PricingBasisDraft source) {}
    private record Stored(PricingBasisDraft draft,int version,String by,String at) {}
    private static BusinessException fail(String code,String message) { return new BusinessException(code,message); }
    private String encode(PricingBasisDraft d) { try { return json.writeValueAsString(d); } catch(Exception e) { throw new IllegalStateException(e); } }
    private PricingBasisDraft decode(String d) { try { return json.readValue(d,PricingBasisDraft.class); } catch(Exception e) { throw new IllegalStateException("Invalid stored pricing basis",e); } }
    private Stored stored(String id) { return jdbc.query("select * from pricing_basis_snapshot where pricing_file_id=?",(r,n)->new Stored(decode(r.getString("draft_json")),r.getInt("version_no"),r.getString("confirmed_by"),r.getString("confirmed_at")),id).stream().findFirst().orElse(null); }
    public View load(String id,PricingBasisDraft source) {
        var s=stored(id); var d=s==null?source:s.draft();
        return new View(d,s==null?0:s.version(),calculator.calculate(d),s==null?null:s.by(),s==null?null:s.at(),!Objects.equals(d.sourceRevisionId(),source.sourceRevisionId()),source);
    }
    private void lockEditable(String id) {
        var states=jdbc.queryForList("select status from pricing_file where id=? for update",String.class,id);
        if(states.isEmpty()) throw fail("PRICING_FILE_NOT_FOUND","核价文件不存在");
        if(!"DRAFT_PACKAGING".equals(states.get(0))) throw fail("PRICING_BASIS_ARCHIVED","核价已归档，不能修改，请生成新版本");
    }
    private void version(Stored s,int expected) {
        if(expected!=(s==null?0:s.version())) throw fail("PRICING_BASIS_CONFLICT","核价草稿已更新，请重新加载；当前输入尚未覆盖服务器数据");
    }
    private void source(PricingBasisDraft d,PricingBasisDraft seed) {
        if(d==null || d.schemaVersion()!=1 || !Objects.equals(d.sourceRevisionId(),seed.sourceRevisionId())) throw fail("PRICING_BASIS_SOURCE_CHANGED","核价来源已变化，请重新取数并确认");
        if(d.materials()==null || d.materials().size()!=seed.materials().size()) throw fail("PRICING_BASIS_SOURCE_MISMATCH","来源物料不完整，请重新取数");
        for(int i=0;i<seed.materials().size();i++) {
            var a=d.materials().get(i); var b=seed.materials().get(i);
            if(a==null || !Objects.equals(List.of(str(a.id()),str(a.majorId()),str(a.stepId()),str(a.sourceMaterialId()),str(a.materialCode()),str(a.materialName()),str(a.role()),str(a.sourceType()),str(a.stage())),List.of(str(b.id()),str(b.majorId()),str(b.stepId()),str(b.sourceMaterialId()),str(b.materialCode()),str(b.materialName()),str(b.role()),str(b.sourceType()),str(b.stage()))) || !same(a.originalWeightKg(),b.originalWeightKg()))
                throw fail("PRICING_BASIS_SOURCE_MISMATCH","来源物料或原始重量不可修改，请重新取数");
        }
        if(d.header()==null || seed.header()==null || !Objects.equals(d.header().productName(),seed.header().productName())) throw fail("PRICING_BASIS_SOURCE_MISMATCH","产品名称必须与来源一致");
    }
    private static String str(String s) { return s==null?"":s; }
    private static boolean same(java.math.BigDecimal a,java.math.BigDecimal b) { return a==null?b==null:b!=null&&a.compareTo(b)==0; }
    @Transactional public View save(String id,int expected,PricingBasisDraft d,PricingBasisDraft seed) {
        lockEditable(id); var s=stored(id); version(s,expected); source(d,seed);
        if(s==null) jdbc.update("insert into pricing_basis_snapshot(pricing_file_id,draft_json,version_no) values(?,?,1)",id,encode(d));
        else jdbc.update("update pricing_basis_snapshot set draft_json=?, version_no=version_no+1,confirmed_by=null,confirmed_at=null where pricing_file_id=?",encode(d),id);
        return load(id,seed);
    }
    public void requireReady(PricingBasisDraft d,PricingBasisDraft seed) {
        source(d,seed);
        var result=calculator.calculate(d);
        if(!result.issues().isEmpty()) throw fail("PRICING_BASIS_INCOMPLETE",result.issues().get(0).message());
        var h=d.header();
        if(List.of(str(h.companyName()),str(h.specification()),str(h.ownerName()),str(h.authorName()),str(h.category()),str(h.documentNumber()),str(h.version()),str(h.effectiveDate())).stream().anyMatch(String::isBlank)) throw fail("PRICING_BASIS_HEADER_REQUIRED","请补全文件信息后确认");
        try { java.time.LocalDate.parse(h.effectiveDate()); } catch(Exception e) { throw fail("PRICING_BASIS_DATE_INVALID","实施日期须为 YYYY-MM-DD"); }
        if(d.packaging()==null||d.packaging().stream().noneMatch(p->p.quantity()!=null&&p.quantity().signum()>0)) throw fail("PRICING_BASIS_PACKAGING_REQUIRED","请确认至少一项本次使用的包装");
        boolean packingBasisChanged=!same(d.finishedNetKg(),seed.finishedNetKg()) || !same(d.unitWeightKg(),seed.unitWeightKg()) || !Objects.equals(d.bagsPerBox(),seed.bagsPerBox());
        var ids=new HashSet<String>();
        for(var p:d.packaging()) {
            if(p==null||str(p.id()).isBlank()||!ids.add(p.id())) throw fail("PRICING_BASIS_PACKAGING_INVALID","包装行标识缺失或重复");
            var original=seed.packaging().stream().filter(x->Objects.equals(x.id(),p.id())).findFirst().orElse(null);
            if(packingBasisChanged && p.quantity().signum()>0 && str(p.adjustmentReason()).isBlank()) throw fail("PRICING_BASIS_PACKAGING_RECONFIRM","包装基准已变化，请逐项核对包装数量并填写确认原因");
            if((original==null || !same(original.quantity(),p.quantity()) || !Objects.equals(original.quantityUnit(),p.quantityUnit()) || !Objects.equals(original.materialName(),p.materialName())) && str(p.adjustmentReason()).isBlank()) throw fail("PRICING_BASIS_ADJUSTMENT_REQUIRED","新增或调整包装数量、单位、名称时请填写调整原因");
        }
        for(var original:seed.packaging()) if(d.packaging().stream().noneMatch(p->Objects.equals(p.id(),original.id()))) throw fail("PRICING_BASIS_PACKAGING_REMOVAL","来源包装不可直接删除，可修改并填写调整原因");
    }
    @Transactional public View confirm(String id,int expected,PricingBasisDraft seed,String actor) {
        lockEditable(id); var s=stored(id); version(s,expected);
        if(s==null) throw fail("PRICING_BASIS_SAVE_REQUIRED","请先保存核价草稿");
        requireReady(s.draft(),seed);
        if(str(actor).isBlank()) throw fail("SESSION_PRINCIPAL_REQUIRED","请重新登录");
        if(s.by()==null) jdbc.update("update pricing_basis_snapshot set confirmed_by=?,confirmed_at=?,version_no=version_no+1 where pricing_file_id=?",actor,LocalDateTime.now().toString(),id);
        return load(id,seed);
    }
    public PricingBasisDraft confirmed(String id,PricingBasisDraft seed) {
        var s=stored(id);
        if(s==null||s.by()==null) throw fail("PRICING_BASIS_CONFIRM_REQUIRED","请先保存并确认核价数据");
        requireReady(s.draft(),seed); return s.draft();
    }
}
