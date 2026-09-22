package com.lhr.rnd.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Operator-facing labels and compact values shared by the R&D exports. */
public final class ExportDisplayFormat {
    public static final String CJK_FONT = "Arial Unicode MS";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> MATERIAL_ROLES = Map.of(
            "PRIMARY", "主料", "AUXILIARY", "辅料", "PROCESS_WATER", "工艺用水");
    private static final Map<String, String> MATERIAL_STATES = Map.ofEntries(
            Map.entry("SOLID", "固态"), Map.entry("LIQUID", "液态"), Map.entry("POWDER", "粉状"),
            Map.entry("FROZEN_SOLID", "冷冻固态"), Map.entry("SEMI_SOLID", "半固态"),
            Map.entry("MARINATED", "腌制状态"), Map.entry("COOKED", "熟制状态"),
            Map.entry("FINISHED", "成品状态"));
    private static final Map<String, String> OUTPUT_TYPES = Map.ofEntries(
            Map.entry("INTERMEDIATE", "中间产物"), Map.entry("FINISHED", "成品"),
            Map.entry("QUALIFIED", "合格产出"), Map.entry("REUSABLE", "余料"),
            Map.entry("TAILING", "尾料"), Map.entry("SAMPLE", "取样"),
            Map.entry("WASTE", "废弃"), Map.entry("HOLD", "留存待处理"));
    private static final Map<String, String> CONTROL_TYPES = Map.ofEntries(
            Map.entry("FOOD_SAFETY", "食品安全"), Map.entry("QUALITY", "质量"),
            Map.entry("PROCESS", "工艺"), Map.entry("SENSORY", "感官"));
    private static final Map<String, String> IMPORTANCE = Map.of(
            "CRITICAL", "关键", "IMPORTANT", "重要", "NORMAL", "一般");
    private static final Map<String, String> RESULTS = Map.ofEntries(
            Map.entry("PASS", "符合"), Map.entry("FAIL", "不符合"),
            Map.entry("CONDITIONAL", "有条件符合"), Map.entry("PENDING", "待确认"));

    private ExportDisplayFormat() {}

    public static String number(BigDecimal value) {
        if (value == null) return "";
        var normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    public static String actualKg(BigDecimal value) {
        return value == null ? "待填写" : number(value) + " kg";
    }

    public static String percent(BigDecimal value) {
        return value == null ? "待填写" : number(value) + "%";
    }

    public static String materialRole(String value) { return label(value, MATERIAL_ROLES); }
    public static String materialState(String value) { return label(value, MATERIAL_STATES); }
    public static String outputType(String value) { return label(value, OUTPUT_TYPES); }
    public static String controlType(String value) { return label(value, CONTROL_TYPES); }
    public static String importance(String value) { return label(value, IMPORTANCE); }
    public static String measurementResult(String value) { return label(value, RESULTS); }

    public static String dateTime(String value) {
        if (blank(value)) return "";
        try {
            return DATE_TIME.format(LocalDateTime.parse(value.trim()));
        } catch (DateTimeParseException ignored) {
            return value.trim().replace('T', ' ').replaceFirst("(\\.\\d+)(Z|[+-]\\d\\d:\\d\\d)?$", "");
        }
    }

    public static String required(String value) { return blank(value) ? "待填写" : value.trim(); }
    public static String optional(String value) { return blank(value) ? "" : value.trim(); }
    public static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String label(String value, Map<String, String> labels) {
        if (blank(value)) return "待核实";
        return labels.getOrDefault(value.trim(), "待核对");
    }
}
