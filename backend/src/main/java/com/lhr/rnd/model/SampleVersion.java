package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SampleVersion(
        String id,
        String projectId,
        String sampleNo,
        String productName,
        String productType,
        String specification,
        String applicationScenario,
        String flavorRequirement,
        String versionNo,
        Integer versionNumber,
        String versionCode,
        String ownerName,
        String authorName,
        LocalDate effectiveDate,
        BigDecimal referenceOutputKg,
        BigDecimal unitWeightKg,
        List<ExperimentMaterial> materials,
        LocalDateTime createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sampleNo;
        private String id;
        private String projectId;
        private String productName;
        private String productType;
        private String specification;
        private String applicationScenario;
        private String flavorRequirement;
        private String versionNo;
        private Integer versionNumber;
        private String versionCode;
        private String ownerName;
        private String authorName;
        private LocalDate effectiveDate;
        private BigDecimal referenceOutputKg;
        private BigDecimal unitWeightKg;
        private List<ExperimentMaterial> materials = List.of();
        private LocalDateTime createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder sampleNo(String sampleNo) {
            this.sampleNo = sampleNo;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder productType(String productType) {
            this.productType = productType;
            return this;
        }

        public Builder specification(String specification) {
            this.specification = specification;
            return this;
        }

        public Builder applicationScenario(String applicationScenario) {
            this.applicationScenario = applicationScenario;
            return this;
        }

        public Builder flavorRequirement(String flavorRequirement) {
            this.flavorRequirement = flavorRequirement;
            return this;
        }

        public Builder versionNo(String versionNo) {
            this.versionNo = versionNo;
            return this;
        }

        public Builder versionNumber(Integer versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public Builder versionCode(String versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }

        public Builder authorName(String authorName) {
            this.authorName = authorName;
            return this;
        }

        public Builder effectiveDate(LocalDate effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder referenceOutputKg(BigDecimal referenceOutputKg) {
            this.referenceOutputKg = referenceOutputKg;
            return this;
        }

        public Builder unitWeightKg(BigDecimal unitWeightKg) {
            this.unitWeightKg = unitWeightKg;
            return this;
        }

        public Builder materials(List<ExperimentMaterial> materials) {
            this.materials = materials == null ? List.of() : List.copyOf(materials);
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public SampleVersion build() {
            return new SampleVersion(
                    id,
                    projectId,
                    sampleNo,
                    productName,
                    productType,
                    specification,
                    applicationScenario,
                    flavorRequirement,
                    versionNo,
                    versionNumber,
                    versionCode == null ? versionNo : versionCode,
                    ownerName,
                    authorName,
                    effectiveDate,
                    referenceOutputKg,
                    unitWeightKg,
                    materials,
                    createdAt
            );
        }
    }
}
