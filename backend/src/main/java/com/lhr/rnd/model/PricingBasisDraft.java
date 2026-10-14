package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.util.List;

/** Pricing-only observations and confirmations; never replaces the experiment graph. */
public record PricingBasisDraft(int schemaVersion, String sourceRevisionId, Header header,
        List<Material> materials, BigDecimal mainOutputKg, List<String> mainMaterialIds,
        String mainScopeBasis, BigDecimal finishedNetKg, BigDecimal unitWeightKg,
        Integer bagsPerBox, Integer actualBags, List<Packaging> packaging, DocumentSignature signature) {
    public PricingBasisDraft(int schemaVersion, String sourceRevisionId, Header header, List<Material> materials,
            BigDecimal mainOutputKg, List<String> mainMaterialIds, String mainScopeBasis, BigDecimal finishedNetKg,
            BigDecimal unitWeightKg, Integer bagsPerBox, Integer actualBags, List<Packaging> packaging) {
        this(schemaVersion, sourceRevisionId, header, materials, mainOutputKg, mainMaterialIds, mainScopeBasis, finishedNetKg, unitWeightKg, bagsPerBox, actualBags, packaging, null);
    }
    public PricingBasisDraft withSignature(DocumentSignature value) {
        var h = header;
        if (h != null) h = new Header(h.companyName(), h.productName(), h.specification(), h.ownerName(), value.compiledBy(), h.category(), h.documentNumber(), h.version(), h.effectiveDate());
        return new PricingBasisDraft(schemaVersion, sourceRevisionId, h, materials, mainOutputKg, mainMaterialIds, mainScopeBasis, finishedNetKg, unitWeightKg, bagsPerBox, actualBags, packaging, value);
    }
    public record Header(String companyName, String productName, String specification,
            String ownerName, String authorName, String category, String documentNumber,
            String version, String effectiveDate) {}
    public record Material(String id, String majorId, String stepId, String sourceMaterialId,
            String stage, String materialCode, String materialName, String role, String sourceType,
            BigDecimal originalWeightKg, String weightBasis, BigDecimal usageKg,
            BigDecimal utilizationRate, String processedOutputId, String confirmationBasis, String remark, String stepName) {
        public Material(String id,String majorId,String stepId,String sourceMaterialId,String stage,String materialCode,String materialName,String role,String sourceType,BigDecimal originalWeightKg,String weightBasis,BigDecimal usageKg,BigDecimal utilizationRate,String processedOutputId,String confirmationBasis,String remark) {
            this(id,majorId,stepId,sourceMaterialId,stage,materialCode,materialName,role,sourceType,originalWeightKg,weightBasis,usageKg,utilizationRate,processedOutputId,confirmationBasis,remark,null);
        }
    }
    public record Packaging(String id, String materialCode, String materialName, String internalCode,
            BigDecimal quantity, String quantityUnit, String specification, String conversionRule,
            String remark, String adjustmentReason) {}
}
