package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_packaging_item")
public class PricingPackagingItemEntity {
    @Id
    private String id;

    @Column(name = "pricing_file_id", nullable = false)
    private String pricingFileId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private String source;

    @Column(name = "material_code")
    private String materialCode;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    private BigDecimal quantity;

    @Column(name = "package_spec")
    private String packageSpec;

    @Column(name = "conversion_rule")
    private String conversionRule;

    private String remark;

    @Column(name = "confirmation_status", nullable = false)
    private String confirmationStatus;

    @Column(name = "modification_reason")
    private String modificationReason;

    protected PricingPackagingItemEntity() {
    }

    public PricingPackagingItemEntity(String id, String pricingFileId, Integer sequence, String source,
                                      String materialCode, String materialName, BigDecimal quantity,
                                      String packageSpec, String conversionRule, String remark,
                                      String confirmationStatus, String modificationReason) {
        this.id = id;
        this.pricingFileId = pricingFileId;
        this.sequence = sequence;
        this.source = source;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.quantity = quantity;
        this.packageSpec = packageSpec;
        this.conversionRule = conversionRule;
        this.remark = remark;
        this.confirmationStatus = confirmationStatus;
        this.modificationReason = modificationReason;
    }

    public String getId() { return id; }
    public String getPricingFileId() { return pricingFileId; }
    public Integer getSequence() { return sequence; }
    public String getSource() { return source; }
    public String getMaterialCode() { return materialCode; }
    public String getMaterialName() { return materialName; }
    public BigDecimal getQuantity() { return quantity; }
    public String getPackageSpec() { return packageSpec; }
    public String getConversionRule() { return conversionRule; }
    public String getRemark() { return remark; }
    public String getConfirmationStatus() { return confirmationStatus; }
    public String getModificationReason() { return modificationReason; }
}
