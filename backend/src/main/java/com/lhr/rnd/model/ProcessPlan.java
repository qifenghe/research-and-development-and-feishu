package com.lhr.rnd.model;

import java.math.BigDecimal;
import java.util.List;

public record ProcessPlan(
        String id,
        String experimentFormId,
        int versionNo,
        String status,
        List<MajorProcess> majorProcesses,
        BigDecimal batchYieldPercent,
        boolean legacy
) {
    public record MajorProcess(
            String id,
            int sequence,
            String processCode,
            String processName,
            String description,
            String yieldBasis,
            String remark,
            List<MinorStep> steps,
            List<ProcessInput> inputs,
            List<ProcessOutput> outputs,
            ProcessYield yield
    ) {
    }

    public record MinorStep(
            String id,
            int sequence,
            String stepCode,
            String stepName,
            String stepType,
            String parameter1Name,
            String parameter1Value,
            String parameter1Unit,
            String parameter2Name,
            String parameter2Value,
            String parameter2Unit,
            String equipment,
            String instruction,
            List<StepMaterial> materials
    ) {
    }

    public record StepMaterial(
            String id,
            int sequence,
            String materialRole,
            String materialCode,
            String materialName,
            String materialState,
            BigDecimal weightKg,
            String formulaMaterialId,
            String remark
    ) {
    }

    public record ProcessInput(
            String id,
            int sequence,
            String inputRole,
            String materialCode,
            String materialName,
            BigDecimal weightKg,
            String sourceStepMaterialId
    ) {
    }

    public record ProcessOutput(
            String id,
            int sequence,
            String outputType,
            BigDecimal weightKg,
            String remark
    ) {
    }

    public record ProcessYield(
            BigDecimal primaryInputWeightKg,
            BigDecimal totalInputWeightKg,
            BigDecimal qualifiedOutputWeightKg,
            BigDecimal reusableOutputWeightKg,
            BigDecimal totalOutputWeightKg,
            BigDecimal mainYieldPercent,
            BigDecimal recoveryPercent,
            BigDecimal balanceDifferenceKg
    ) {
    }
}
