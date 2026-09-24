import { aggregateProcessRecipe, type ProcessPlanDraft } from "../../../../../packages/shared/src/process-plan.ts";
import { observedTotal } from "./actualSummary.ts";

export function buildRecipeSummary(plan: ProcessPlanDraft) {
  const additions = plan.majorProcesses.flatMap(major => major.steps.flatMap(step => step.materials
    .filter(material => material.sourceType === "EXTERNAL")
    .map(material => ({major, step, material}))));
  const externalTotalKg = observedTotal(additions.map(item => item.material));
  const rows = aggregateProcessRecipe(plan).map(line => {
    const occurrences = line.sources.map(source => {
      const matches = additions.filter(({major,step,material}) => major.sequence === source.majorSequence
        && step.sequence === source.stepSequence && material.sequence === source.materialSequence);
      const match = matches.length === 1 ? matches[0] : undefined;
      const value = match?.material.weightKg;
      return {majorName:match?.major.processName || "工序待核实", stepName:match?.step.stepName || "步骤待核实",
        weightKg:value != null && Number.isFinite(value) && value >= 0 ? value : null,
        additionTiming:match?.material.additionTiming, additionMethod:match?.material.additionMethod};
    });
    const weightKg = occurrences.every(item => item.weightKg != null) && Number.isFinite(line.weightKg) ? line.weightKg : null;
    return {materialName:line.materialName, materialCode:line.materialCode, formulaMaterialId:line.formulaMaterialId,
      materialRole:line.canonicalMaterialRole, weightKg,
      proportionPercent:weightKg != null && externalTotalKg != null && externalTotalKg > 0 ? line.ratioPercent : null, occurrences};
  });
  return {externalTotalKg:Number.isFinite(externalTotalKg) ? externalTotalKg : null, rows};
}
