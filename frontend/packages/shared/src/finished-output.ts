export type FinishedOutputUnit = '袋'|'盒'|'份'|'个'|'盘'|'kg';
export interface FinishedOutputState {weightKg:number|null;quantity:number|null;unit:FinishedOutputUnit}
export function normalizeFinishedOutput(weightKg:number|null|undefined,quantity:number|null|undefined,unit:FinishedOutputUnit):FinishedOutputState {
 return {weightKg:weightKg??null,quantity:unit==='kg'?(weightKg??null):(quantity??null),unit};
}
export function switchFinishedOutputUnit(state:FinishedOutputState,nextUnit:FinishedOutputUnit):FinishedOutputState {
 return normalizeFinishedOutput(state.weightKg,state.unit==='kg'&&nextUnit!=='kg'?null:state.quantity,nextUnit);
}
