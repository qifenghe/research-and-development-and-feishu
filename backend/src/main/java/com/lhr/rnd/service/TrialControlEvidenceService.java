package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.TrialSchemeCopyService;
import com.lhr.rnd.model.ProcessPlan;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** Only persisted trial JSON is an evidence source; never the incoming save document. */
@Service
class TrialControlEvidenceService {
    private final ObjectMapper mapper;
    private final TrialSchemeCopyService copies;

    TrialControlEvidenceService(ObjectMapper mapper, TrialSchemeCopyService copies) {
        this.mapper = mapper;
        this.copies = copies;
    }

    ProcessPlan preserveUnchanged(ProcessPlan normalized, ProcessPlan persisted) {
        var tree = tree(normalized);
        var old = locations(tree(persisted));
        for (var location : locations(tree).values()) {
            var previous = old.get(location.point().path("id").asText());
            if (previous != null && previous.majorId().equals(location.majorId()) && previous.stepId().equals(location.stepId())
                    && signature(previous.point()).equals(signature(location.point()))) {
                transfer(previous.point(), location.point());
            }
        }
        return plan(tree);
    }

    ProcessPlan confirm(ProcessPlan source, String pointId, Set<String> inherited, SessionPrincipal principal,
                        boolean deviation, String note) {
        var tree = tree(source);
        var location = locations(tree).get(pointId);
        if (location == null) throw error("TRIAL_CONTROL_NOT_FOUND", "试验控制点不存在");
        var point = mapper.convertValue(location.point(), ProcessPlan.ControlPoint.class);
        requireFresh(point, inherited);
        var measurements = values(point.measurements());
        if (measurements.isEmpty() || measurements.stream().anyMatch(m -> m.measuredValue() == null || blank(m.measuredAt())
                || !("PASS".equals(m.result()) || "FAIL".equals(m.result())))) {
            throw error("TRIAL_CONTROL_NOT_PASSING", "请先记录完整有效的实测值、时间和判定");
        }
        if (point.lowerLimit() != null && point.upperLimit() != null && point.lowerLimit().compareTo(point.upperLimit()) > 0) {
            throw error("TRIAL_CONTROL_NOT_PASSING", "控制上下限无效");
        }
        var failures = measurements.stream().filter(m -> failed(point, m)).toList();
        if (!deviation && !failures.isEmpty()) throw error("TRIAL_CONTROL_NOT_PASSING", "存在偏差，请由总监确认处理及复测");
        if (deviation) {
            if (blank(note) || note.length() > 2000 || failures.isEmpty() || failures.stream().anyMatch(m -> blank(m.deviationAction()))) {
                throw error("TRIAL_DEVIATION_NOT_RESOLVED", "请填写偏差处置说明并记录合格复测");
            }
            var lastFailure = failures.stream().map(m -> LocalDateTime.parse(m.measuredAt())).max(Comparator.naturalOrder()).orElseThrow();
            if (measurements.stream().noneMatch(m -> !failed(point, m) && "PASS".equals(m.result())
                    && LocalDateTime.parse(m.measuredAt()).isAfter(lastFailure))) {
                throw error("TRIAL_DEVIATION_NOT_RESOLVED", "偏差之后必须有独立的合格复测记录");
            }
            for (var m : location.point().path("measurements")) {
                var measurement = mapper.convertValue(m, ProcessPlan.ControlMeasurement.class);
                if (failed(point, measurement)) ((ObjectNode)m).put("retestResult", "PASS");
            }
        }
        location.point().put("resolved", deviation).put("confirmedBy", principal.name()).put("confirmedAt", LocalDateTime.now().toString());
        return plan(tree);
    }

    /** Remapper preserves list correspondence. Transfer only a trusted source's release evidence. */
    ProcessPlan transferRemapped(ProcessPlan persisted, ProcessPlan remapped, Set<String> inherited) {
        var result = tree(remapped);
        var old = new ArrayList<>(locations(tree(persisted)).values());
        var next = new ArrayList<>(locations(result).values());
        if (old.size() != next.size()) throw new IllegalStateException("Control remapping lost correspondence");
        for (int i = 0; i < old.size(); i++) {
            var point = mapper.convertValue(old.get(i).point(), ProcessPlan.ControlPoint.class);
            if (blank(point.confirmedBy()) || blank(point.confirmedAt())) continue;
            requireFresh(point, inherited);
            transfer(old.get(i).point(), next.get(i).point());
        }
        return plan(result);
    }

    private void requireFresh(ProcessPlan.ControlPoint point, Set<String> inherited) {
        if (values(point.measurements()).stream().anyMatch(m -> inherited.contains(copies.measurementFingerprint(m)))) {
            throw error("TRIAL_INHERITED_MEASUREMENT", "继承的实测不能作为本次试验重新确认，请完成新的实测");
        }
    }

    private void transfer(ObjectNode from, ObjectNode to) {
        for (var field : List.of("resolved", "confirmedBy", "confirmedAt")) to.set(field, from.path(field).deepCopy());
        for (int i = 0; i < from.path("measurements").size(); i++) {
            ((ObjectNode)to.path("measurements").get(i)).set("retestResult", from.path("measurements").get(i).path("retestResult").deepCopy());
        }
    }

    private JsonNode signature(ObjectNode point) {
        var result = point.deepCopy();
        result.remove(List.of("resolved", "confirmedBy", "confirmedAt"));
        for (var measurement : result.path("measurements")) ((ObjectNode)measurement).remove("retestResult");
        return canonical(result);
    }

    /** Stable comparison of graph content: remap IDs/references, omit lifecycle/audit/derived caches. */
    String graphContent(ProcessPlan source) {
        var root = tree(source);
        root.remove(List.of("id", "experimentFormId", "versionNo", "status", "legacy", "sourceRevisionId", "changeReason", "batchYieldPercent"));
        var ids = new LinkedHashMap<String, String>();
        collectIds(root, ids);
        replaceIds(root, ids);
        for (var major : root.path("majorProcesses")) ((ObjectNode)major).remove("yield");
        return canonical(root).toString();
    }

    private void collectIds(JsonNode node, Map<String, String> ids) {
        if (node.isObject()) {
            if (node.hasNonNull("id")) ids.computeIfAbsent(node.path("id").asText(), k -> "node-" + ids.size());
            node.elements().forEachRemaining(child -> collectIds(child, ids));
        } else if (node.isArray()) node.forEach(child -> collectIds(child, ids));
    }

    private void replaceIds(JsonNode node, Map<String, String> ids) {
        if (node.isObject()) {
            var object = (ObjectNode)node;
            object.remove(List.of("confirmedBy", "confirmedAt", "resolved", "retestResult"));
            for (var field : List.of("id", "sourceStepMaterialId", "sourceStepOutputId")) {
                if (node.hasNonNull(field)) object.put(field, ids.getOrDefault(node.path(field).asText(), node.path(field).asText()));
            }
            node.elements().forEachRemaining(child -> replaceIds(child, ids));
        } else if (node.isArray()) node.forEach(child -> replaceIds(child, ids));
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isNumber()) return TextNode.valueOf(node.decimalValue().stripTrailingZeros().toPlainString());
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                var value = node.get(name);
                if ("measuredAt".equals(name) && value.isTextual() && !value.asText().isBlank()) {
                    result.put(name, LocalDateTime.parse(value.asText()).toString());
                } else result.set(name, canonical(value));
            }
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(child -> result.add(canonical(child)));
            return result;
        }
        return node;
    }

    private Map<String, Location> locations(ObjectNode plan) {
        var result = new LinkedHashMap<String, Location>();
        for (var major : plan.path("majorProcesses")) for (var step : major.path("steps")) for (var point : step.path("controlPoints")) {
            result.put(point.path("id").asText(), new Location(major.path("id").asText(), step.path("id").asText(), (ObjectNode)point));
        }
        return result;
    }
    private boolean failed(ProcessPlan.ControlPoint point, ProcessPlan.ControlMeasurement m) {
        return "FAIL".equals(m.result()) || m.measuredValue() != null && (point.lowerLimit() != null && m.measuredValue().compareTo(point.lowerLimit()) < 0
                || point.upperLimit() != null && m.measuredValue().compareTo(point.upperLimit()) > 0);
    }
    private ObjectNode tree(ProcessPlan value) { return mapper.valueToTree(value); }
    private ProcessPlan plan(ObjectNode value) { return mapper.convertValue(value, ProcessPlan.class); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static <T> List<T> values(List<T> list) { return list == null ? List.of() : list; }
    private static BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private record Location(String majorId, String stepId, ObjectNode point) {}
}
