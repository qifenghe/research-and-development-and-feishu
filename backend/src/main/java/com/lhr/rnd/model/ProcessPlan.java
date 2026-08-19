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
        BigDecimal balanceToleranceKg,
        boolean legacy,
        String sourceRevisionId,
        String changeReason
) {
    public static final BigDecimal DEFAULT_BALANCE_TOLERANCE_KG = new BigDecimal("0.0100");

    public ProcessPlan {
        balanceToleranceKg = balanceToleranceKg == null ? DEFAULT_BALANCE_TOLERANCE_KG : balanceToleranceKg;
    }

    public ProcessPlan(
            String id,
            String experimentFormId,
            int versionNo,
            String status,
            List<MajorProcess> majorProcesses,
            BigDecimal batchYieldPercent,
            BigDecimal balanceToleranceKg,
            boolean legacy
    ) {
        this(id, experimentFormId, versionNo, status, majorProcesses, batchYieldPercent,
                balanceToleranceKg, legacy, null, null);
    }

    public ProcessPlan(
            String id,
            String experimentFormId,
            int versionNo,
            String status,
            List<MajorProcess> majorProcesses,
            BigDecimal batchYieldPercent,
            boolean legacy
    ) {
        this(id, experimentFormId, versionNo, status, majorProcesses, batchYieldPercent,
                DEFAULT_BALANCE_TOLERANCE_KG, legacy, null, null);
    }

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
            List<StepMaterial> materials,
            List<StepOutput> outputs,
            List<ControlPoint> controlPoints
    ) {
        public MinorStep(
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
            this(id, sequence, stepCode, stepName, stepType, parameter1Name, parameter1Value, parameter1Unit,
                    parameter2Name, parameter2Value, parameter2Unit, equipment, instruction, materials, List.of(), List.of());
        }
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
            String remark,
            String sourceType,
            String sourceStepOutputId
    ) {
        public StepMaterial(
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
            this(id, sequence, materialRole, materialCode, materialName, materialState, weightKg, formulaMaterialId, remark,
                    "EXTERNAL", null);
        }
    }

    public record StepOutput(
            String id,
            int sequence,
            String outputType,
            String outputName,
            String materialState,
            BigDecimal weightKg,
            boolean primaryOutput,
            boolean continueFlow,
            String remark
    ) {
    }

    public record ControlPoint(
            String id,
            int sequence,
            String controlType,
            String importance,
            String itemName,
            BigDecimal targetValue,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            String unit,
            String method,
            String measurementTool,
            String frequency,
            String deviationAction,
            boolean resolved,
            String confirmedBy,
            String confirmedAt,
            String basisOrRemark,
            List<ControlMeasurement> measurements
    ) {
        public ControlPoint(
                String id,
                int sequence,
                String controlType,
                String importance,
                String itemName,
                BigDecimal targetValue,
                BigDecimal lowerLimit,
                BigDecimal upperLimit,
                String unit,
                String method,
                String frequency,
                String deviationAction,
                boolean resolved,
                String confirmedBy,
                List<ControlMeasurement> measurements
        ) {
            this(id, sequence, controlType, importance, itemName, targetValue, lowerLimit, upperLimit, unit, method,
                    null, frequency, deviationAction, resolved, confirmedBy, null, null, measurements);
        }
    }

    public record ControlMeasurement(
            String id,
            int sequence,
            BigDecimal measuredValue,
            String measuredAt,
            String result,
            String deviationAction,
            String retestResult,
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
