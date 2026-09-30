package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sample_request")
public class SampleRequestEntity {
    @Id
    private String id;

    @Column(name = "sample_no", nullable = false, unique = true)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "specification", nullable = false)
    private String specification;

    @Column(name = "application_scenario")
    private String applicationScenario;

    @Column(name = "flavor_requirement")
    private String flavorRequirement;

    @Column(name = "creator_name", nullable = false)
    private String creatorName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SampleRequestEntity() {
    }

    @Column(name="reference_sample_provided") private Boolean referenceSampleProvided;
    public Boolean referenceSampleProvided(){return referenceSampleProvided;}

    @Column(name="cut_shape") private String cutShape;
    public String cutShape(){return cutShape;}

    @Column(name="main_material_requirement") private String mainMaterialRequirement;
    public String mainMaterialRequirement(){return mainMaterialRequirement;}

    @Column(name="texture_requirement") private String textureRequirement;
    public String textureRequirement(){return textureRequirement;}

    @Column(name="packaging_requirement") private String packagingRequirement;
    public String packagingRequirement(){return packagingRequirement;}

    @Column(name="storage_requirement") private String storageRequirement;
    public String storageRequirement(){return storageRequirement;}

    @Column(name="customer_target_price") private String customerTargetPrice;
    public String customerTargetPrice(){return customerTargetPrice;}

    @Column(name="monthly_demand") private String monthlyDemand;
    public String monthlyDemand(){return monthlyDemand;}

    @Column(name="special_requirements") private String specialRequirements;
    public String specialRequirements(){return specialRequirements;}

    @Column(name="reference_market_price") private String referenceMarketPrice;
    public String referenceMarketPrice(){return referenceMarketPrice;}

    @Column(name="additional_notes") private String additionalNotes;
    public String additionalNotes(){return additionalNotes;}

    public SampleRequestEntity(String id,String sampleNo,String productName,String productType,String customerName,
            String specification,String applicationScenario,String flavorRequirement,String creatorName,String status,LocalDateTime createdAt,
        Boolean referenceSampleProvided,
        String cutShape,
        String mainMaterialRequirement,
        String textureRequirement,
        String packagingRequirement,
        String storageRequirement,
        String customerTargetPrice,
        String monthlyDemand,
        String specialRequirements,
        String referenceMarketPrice,
        String additionalNotes) {
        this(id,sampleNo,productName,productType,customerName,specification,applicationScenario,flavorRequirement,creatorName,status,createdAt);
        this.referenceSampleProvided=referenceSampleProvided;
        this.cutShape=cutShape;
        this.mainMaterialRequirement=mainMaterialRequirement;
        this.textureRequirement=textureRequirement;
        this.packagingRequirement=packagingRequirement;
        this.storageRequirement=storageRequirement;
        this.customerTargetPrice=customerTargetPrice;
        this.monthlyDemand=monthlyDemand;
        this.specialRequirements=specialRequirements;
        this.referenceMarketPrice=referenceMarketPrice;
        this.additionalNotes=additionalNotes;
    }

    public SampleRequestEntity(
            String id,
            String sampleNo,
            String productName,
            String productType,
            String customerName,
            String specification,
            String applicationScenario,
            String flavorRequirement,
            String creatorName,
            String status,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.productType = productType;
        this.customerName = customerName;
        this.specification = specification;
        this.applicationScenario = applicationScenario;
        this.flavorRequirement = flavorRequirement;
        this.creatorName = creatorName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String sampleNo() {
        return sampleNo;
    }

    public String productName() {
        return productName;
    }

    public String productType() {
        return productType;
    }

    public String customerName() {
        return customerName;
    }

    public String specification() {
        return specification;
    }

    public String applicationScenario() {
        return applicationScenario;
    }

    public String flavorRequirement() {
        return flavorRequirement;
    }

    public String creatorName() {
        return creatorName;
    }

    public String status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
