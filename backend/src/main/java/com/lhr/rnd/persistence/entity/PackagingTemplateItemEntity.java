package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "packaging_template_item")
public class PackagingTemplateItemEntity {
    @Id
    private String id;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "material_code")
    private String materialCode;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "conversion_type", nullable = false)
    private String conversionType;

    @Column(name = "units_per_parent", nullable = false)
    private BigDecimal unitsPerParent;

    @Column(name = "quantity_unit")
    private String quantityUnit;

    @Column(name = "package_spec")
    private String packageSpec;

    private String remark;

    @Column(nullable = false)
    private Boolean enabled;

    protected PackagingTemplateItemEntity() {
    }

    public String getId() { return id; }
    public String getQuantityUnit() { return quantityUnit; }
    public String getTemplateCode() { return templateCode; }
    public Integer getSequence() { return sequence; }
    public String getMaterialCode() { return materialCode; }
    public String getMaterialName() { return materialName; }
    public String getConversionType() { return conversionType; }
    public BigDecimal getUnitsPerParent() { return unitsPerParent; }
    public String getPackageSpec() { return packageSpec; }
    public String getRemark() { return remark; }
    public Boolean getEnabled() { return enabled; }
}
