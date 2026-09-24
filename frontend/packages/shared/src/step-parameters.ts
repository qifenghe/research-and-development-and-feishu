export interface StepParameterDraft {
  key: string;
  name: string;
  valueType: "NUMBER" | "TEXT";
  unit?: string;
  actualValue?: string;
  lowerLimit?: string;
  upperLimit?: string;
  measurementBasis?: string;
  remark?: string;
  showInSummary: boolean;
}
type ParameterStep = {
  parameters?: StepParameterDraft[] | null;
  parameter1Name?: string; parameter1Value?: string; parameter1Unit?: string;
  parameter2Name?: string; parameter2Value?: string; parameter2Unit?: string;
};
export function isParameterNumber(value: unknown): value is string {
  return typeof value === "string" && /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value.trim()) && Number.isFinite(Number(value));
}
export function effectiveStepParameters(step: ParameterStep): StepParameterDraft[] {
  if (step.parameters != null) return step.parameters.map(p => ({...p}));
  return ([1,2] as const).flatMap(i => {
    const name=step[`parameter${i}Name`] || "", actualValue=step[`parameter${i}Value`], unit=step[`parameter${i}Unit`];
    if (![name,actualValue,unit].some(v => v?.trim())) return [];
    return [{key:`legacy-${i}`,name,actualValue,unit,valueType:isParameterNumber(actualValue)?"NUMBER" as const:"TEXT" as const,showInSummary:true}];
  });
}
