package com.lhr.rnd.domain;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessPlan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class StepParameterService {
    private StepParameterService() {}
    public static List<ProcessPlan.StepParameter> effective(ProcessPlan.MinorStep step) {
        if (step.parameters() != null) return step.parameters();
        var result = new ArrayList<ProcessPlan.StepParameter>();
        legacy(result,"legacy-1",step.parameter1Name(),step.parameter1Value(),step.parameter1Unit());
        legacy(result,"legacy-2",step.parameter2Name(),step.parameter2Value(),step.parameter2Unit());
        return List.copyOf(result);
    }
    private static void legacy(List<ProcessPlan.StepParameter> result, String key, String name, String value, String unit) {
        if (blank(name) && blank(value) && blank(unit)) return;
        result.add(new ProcessPlan.StepParameter(key,name,numeric(value) ? "NUMBER" : "TEXT",unit,value,null,null,null,null,true));
    }
    public static boolean blank(String value) { return value == null || value.isBlank(); }
    public static boolean numeric(String value) {
        if (blank(value) || !value.trim().matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")) return false;
        try { return Double.isFinite(new BigDecimal(value.trim()).doubleValue()); }
        catch (NumberFormatException e) { return false; }
    }
    public static void validate(ProcessPlan.MinorStep step) {
        if (step.parameters() == null) return; // Preserve legacy snapshots, including free-form historical values.
        var keys = new HashSet<String>();
        for (var p : step.parameters()) {
            if (p == null || blank(p.key()) || !keys.add(p.key())) fail("参数键不能为空或重复");
            if (!"NUMBER".equals(p.valueType()) && !"TEXT".equals(p.valueType())) fail("参数类型不支持");
            if ("NUMBER".equals(p.valueType())) {
                for (String value : new String[]{p.actualValue(),p.lowerLimit(),p.upperLimit()}) {
                    if (!blank(value) && !numeric(value)) fail("数值参数必须为完整有限数值");
                }
                if (!blank(p.lowerLimit()) && !blank(p.upperLimit()) &&
                        new BigDecimal(p.lowerLimit().trim()).compareTo(new BigDecimal(p.upperLimit().trim())) > 0) fail("参数下限不能大于上限");
            } else if (!blank(p.lowerLimit()) || !blank(p.upperLimit())) fail("文字参数不能设置数值范围");
        }
    }
    private static void fail(String message) { throw new BusinessException("STEP_PARAMETER_INVALID",message); }
}
