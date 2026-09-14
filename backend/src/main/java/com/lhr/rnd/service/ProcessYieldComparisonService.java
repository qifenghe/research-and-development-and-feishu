package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.model.ProcessPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProcessYieldComparisonService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ProcessPlanService plans;
    public ProcessYieldComparisonService(JdbcTemplate jdbc, ObjectMapper mapper, ProcessPlanService plans) {
        this.jdbc = jdbc; this.mapper = mapper; this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<Row> compare(String formId, SessionPrincipal principal) {
        plans.requireDraftReadAccess(formId, principal);
        // Access is checked before reading any other product's formula snapshot.
        var candidates = jdbc.query("""
            select r.id, r.experiment_form_id, r.revision_no, r.submitted_at, f.product_name, f.sample_no
            from experiment_process_revision r join experiment_form f on f.id=r.experiment_form_id
            where r.revision_no=(select max(x.revision_no) from experiment_process_revision x where x.experiment_form_id=r.experiment_form_id)
            order by r.submitted_at desc, r.id
            """, (rs, n) -> new Candidate(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getTimestamp(4).toString(), rs.getString(5), rs.getString(6)));
        var result = new ArrayList<Row>();
        int visible = 0;
        for (var candidate : candidates) {
            if (!plans.canReadForComparison(candidate.formId(), principal)) continue;
            if (++visible > 200) break;
            var json = jdbc.queryForObject("select snapshot_json from experiment_process_revision where id=?", String.class, candidate.id());
            ProcessPlan plan;
            try { plan = mapper.readValue(json, ProcessPlan.class); }
            catch (java.io.IOException ex) { throw new BusinessException("PROCESS_SNAPSHOT_INVALID", "正式工艺快照无法读取，请检查版本 " + candidate.id()); }
            if (!new ProcessSubmissionValidator().flowIssues(plan).isEmpty()) continue;
            for (var major : plan.majorProcesses()) {
                if ("NONE".equals(major.yieldBasis()) || major.processCode() == null || major.processCode().isBlank()) continue;
                var inputs = major.steps().stream().flatMap(s -> s.materials().stream()).filter(m -> "PRIMARY".equals(m.materialRole())).toList();
                var outputs = major.steps().stream().flatMap(s -> s.outputs().stream()).filter(ProcessPlan.StepOutput::primaryOutput).toList();
                if (inputs.isEmpty() || outputs.isEmpty()) continue;
                var input = inputs.get(0); var output = outputs.get(outputs.size()-1);
                var value = new ProcessPlanCalculationService().calculate(major);
                if (value.mainYieldPercent() == null) continue;
                var materialIdentity = input.formulaMaterialId() != null ? input.formulaMaterialId()
                        : input.materialCode() != null ? input.materialCode() : input.materialName();
                String groupKey;
                try { groupKey = mapper.writeValueAsString(java.util.Arrays.asList(major.processCode(), major.yieldBasis(), materialIdentity, input.materialState(), output.materialState())); }
                catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                result.add(new Row(candidate.id()+":"+major.sequence(), groupKey, candidate.formId(), candidate.id(), candidate.revisionNo(),
                        candidate.productName(), candidate.sampleNo(), candidate.submittedAt(), major.processCode(), major.processName(), major.sequence(),
                        input.materialName(), input.materialState(), output.materialState(), value.primaryInputWeightKg(), value.qualifiedOutputWeightKg(), value.mainYieldPercent()));
            }
        }
        return result;
    }
    private record Candidate(String id, String formId, int revisionNo, String submittedAt, String productName, String sampleNo) {}
    public record Row(String id, String groupKey, String formId, String revisionId, int revisionNo, String productName, String sampleNo,
                      String submittedAt, String processCode, String processName, int sequence, String materialName, String inputState, String outputState,
                      BigDecimal inputKg, BigDecimal outputKg, BigDecimal yieldPercent) {}
}
