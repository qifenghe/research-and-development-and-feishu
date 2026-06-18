package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SampleVersion(
        String sampleNo,
        String productName,
        String productType,
        String specification,
        String versionNo,
        String ownerName,
        String authorName,
        LocalDate effectiveDate,
        BigDecimal referenceOutputKg,
        BigDecimal unitWeightKg,
        List<ExperimentMaterial> materials
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sampleNo;
        private String productName;
        private String productType;
        private String specification;
        private String versionNo;
        private String ownerName;
        private String authorName;
        private LocalDate effectiveDate;
        private BigDecimal referenceOutputKg;
        private BigDecimal unitWeightKg;
        private List<ExperimentMaterial> materials = List.of();

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

        public Builder versionNo(String versionNo) {
            this.versionNo = versionNo;
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

        public SampleVersion build() {
            return new SampleVersion(
                    sampleNo,
                    productName,
                    productType,
                    specification,
                    versionNo,
                    ownerName,
                    authorName,
                    effectiveDate,
                    referenceOutputKg,
                    unitWeightKg,
                    materials
            );
        }
    }
}
