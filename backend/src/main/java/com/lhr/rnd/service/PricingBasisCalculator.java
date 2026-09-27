package com.lhr.rnd.service;

import com.lhr.rnd.model.PricingBasisDraft;
import com.lhr.rnd.model.PricingBasisResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PricingBasisCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static boolean nonnegative(BigDecimal n) { return n != null && n.signum() >= 0; }
    private static boolean positive(BigDecimal n) { return n != null && n.signum() > 0; }
    private static boolean rate(BigDecimal n) { return positive(n) && n.compareTo(BigDecimal.ONE) <= 0; }
    private static BigDecimal divide(BigDecimal a, BigDecimal b) { return a.divide(b, 12, RoundingMode.HALF_UP); }
    private static void issue(List<PricingBasisResult.Issue> issues, String path, String message) {
        issues.add(new PricingBasisResult.Issue(path, message));
    }

    public PricingBasisResult calculate(PricingBasisDraft draft) {
        var issues = new ArrayList<PricingBasisResult.Issue>();
        var rows = new ArrayList<PricingBasisResult.Material>();
        var materials = draft.materials() == null ? List.<PricingBasisDraft.Material>of() : draft.materials();
        var ids = new HashSet<String>();
        boolean complete = !materials.isEmpty();
        if (materials.isEmpty()) issue(issues, "materials", "请确认外部原辅料");
        for (int index = 0; index < materials.size(); index++) {
            var m = materials.get(index);
            String path = "materials[" + index + "]";
            int before = issues.size();
            if (m == null) { issue(issues, path, "物料记录缺失"); complete = false; continue; }
            if (blank(m.id()) || !ids.add(m.id())) issue(issues, path + ".id", "物料行标识缺失或重复");
            if (!"EXTERNAL".equals(m.sourceType())) issue(issues, path + ".sourceType", "只允许外部物料参与领料");
            if (blank(m.majorId()) || blank(m.stepId()) || blank(m.sourceMaterialId())) issue(issues, path, "投料来源未确认");
            if (!nonnegative(m.originalWeightKg())) issue(issues, path + ".originalWeightKg", "原始投入重量缺失或为负");
            if (!nonnegative(m.usageKg())) issue(issues, path + ".usageKg", "请填写非负配方用量");
            BigDecimal r = null, q = null;
            if ("AFTER_PROCESS".equals(m.weightBasis())) {
                if (!rate(m.utilizationRate())) issue(issues, path + ".utilizationRate", "参考利用率须大于0且不超过100%");
                else if (nonnegative(m.usageKg())) { r = m.utilizationRate(); q = divide(m.usageKg(), r); }
            } else if ("BEFORE_PROCESS".equals(m.weightBasis())) {
                if (!positive(m.originalWeightKg())) issue(issues, path + ".originalWeightKg", "处理前领料基准必须大于0");
                if (blank(m.confirmationBasis())) issue(issues, path + ".confirmationBasis", "请确认处理后净用量的归属依据");
                if (positive(m.originalWeightKg()) && nonnegative(m.usageKg())) {
                    r = divide(m.usageKg(), m.originalWeightKg());
                    if (!rate(r)) issue(issues, path + ".usageKg", "处理后用量须大于0且不超过领料量");
                    else q = m.originalWeightKg();
                }
            } else issue(issues, path + ".weightBasis", "请选择重量口径");
            if (before != issues.size()) { q = null; complete = false; }
            rows.add(new PricingBasisResult.Material(m.id(), m.usageKg(), r, q));
        }
        BigDecimal usageTotal = complete ? rows.stream().map(PricingBasisResult.Material::usageKg).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal requisitionTotal = complete ? rows.stream().map(PricingBasisResult.Material::requisitionKg).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal yield = null;
        var selected = draft.mainMaterialIds() == null ? List.<String>of() : draft.mainMaterialIds();
        boolean scopeValid = !selected.isEmpty() && new HashSet<>(selected).size() == selected.size() && !blank(draft.mainScopeBasis());
        for (String id : selected) if (id == null || materials.stream().noneMatch(m -> m != null && id.equals(m.id()) && "PRIMARY".equals(m.role()))) scopeValid = false;
        if (!scopeValid) issue(issues, "mainMaterialIds", "请确认最终主料产出对应的主料领料范围与依据");
        if (!nonnegative(draft.mainOutputKg())) issue(issues, "mainOutputKg", "请确认非负主料出成");
        if (scopeValid && complete && nonnegative(draft.mainOutputKg())) {
            BigDecimal denominator = rows.stream().filter(m -> selected.contains(m.id())).map(PricingBasisResult.Material::requisitionKg).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (positive(denominator)) yield = divide(draft.mainOutputKg().multiply(HUNDRED), denominator);
            else issue(issues, "mainMaterialIds", "主料领料量必须大于0才能计算得率");
        }
        Long bags = null, boxes = null; BigDecimal remaining = null;
        if (!nonnegative(draft.finishedNetKg())) issue(issues, "finishedNetKg", "请确认独立可分装成品净重");
        if (!positive(draft.unitWeightKg())) issue(issues, "unitWeightKg", "单包净重必须大于0");
        if (draft.bagsPerBox() == null || draft.bagsPerBox() <= 0) issue(issues, "bagsPerBox", "每箱包数须为正整数");
        if (draft.actualBags() != null && draft.actualBags() < 0) issue(issues, "actualBags", "实测包数不能为负");
        if (nonnegative(draft.finishedNetKg()) && positive(draft.unitWeightKg())) {
            try {
                bags = draft.finishedNetKg().divide(draft.unitWeightKg(), 0, RoundingMode.DOWN).longValueExact();
                remaining = draft.finishedNetKg().subtract(draft.unitWeightKg().multiply(BigDecimal.valueOf(bags)));
                if (draft.bagsPerBox() != null && draft.bagsPerBox() > 0) boxes = BigDecimal.valueOf(bags).divide(BigDecimal.valueOf(draft.bagsPerBox()), 0, RoundingMode.CEILING).longValueExact();
            } catch (ArithmeticException e) { bags = null; boxes = null; remaining = null; issue(issues, "finishedNetKg", "参考数量超出支持范围"); }
        }
        if (draft.packaging() != null) for (int i = 0; i < draft.packaging().size(); i++) {
            var p = draft.packaging().get(i); String path = "packaging[" + i + "]";
            if (p == null) { issue(issues,path,"包装记录缺失"); continue; }
            if (!positive(p.quantity())) issue(issues,path + ".quantity","包装数量必须大于0");
            if (blank(p.materialName())) issue(issues,path + ".materialName","请填写包装名称");
            if (blank(p.quantityUnit())) issue(issues,path + ".quantityUnit","请填写包装数量单位");
        }
        return new PricingBasisResult(List.copyOf(rows), usageTotal, requisitionTotal, yield, bags, boxes, remaining, List.copyOf(issues));
    }
}
