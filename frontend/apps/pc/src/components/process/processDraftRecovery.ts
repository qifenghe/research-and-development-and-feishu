import type { ProcessPlanDraft } from '../../../../../packages/shared/src/process-plan';
import { normalizeProcessPlan } from '../../../../../packages/shared/src/process-plan.ts';

export function hasLayeredProcessData(plan: ProcessPlanDraft): boolean {
  return plan.majorProcesses.some(major => major.steps.length > 0);
}

export function recoverProcessDraft(server: ProcessPlanDraft, cached: ProcessPlanDraft): 'server' | 'local' | 'conflict' {
  if (content(server) === content(cached)) return 'server';
  if (server.status === 'DRAFT' && cached.status === 'DRAFT' && server.versionNo === cached.versionNo) return 'local';
  return 'conflict';
}

// Ignore transport identities and derived values, never weights, parameters or confirmations.
function content(value: ProcessPlanDraft): string {
  const plan = normalizeProcessPlan(value);
  const outputs = new Map<string, string>();
  plan.majorProcesses.forEach((major, m) => major.steps.forEach((step, s) => (step.outputs || []).forEach((output, o) => {
    for (const key of [output.id, output.key]) if (key) outputs.set(key, `${m}/${s}/${o}`);
  })));
  function clean(value: unknown, field = ''): unknown {
    if (field === 'sourceStepOutputId' && typeof value === 'string') return outputs.get(value) ?? value;
    if (Array.isArray(value)) return value.map(item => clean(item));
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value)
      .filter(([key, item]) => !['id','key','yield'].includes(key) && item != null && item !== '')
      .sort(([a],[b]) => a.localeCompare(b)).map(([key,item]) => [key, clean(item,key)]));
    return value;
  }
  return JSON.stringify(clean({majorProcesses:plan.majorProcesses, balanceToleranceKg:plan.balanceToleranceKg,
    sourceRevisionId:plan.sourceRevisionId, changeReason:plan.changeReason}));
}

export function templateProcessCode(name: string): string {
  return ({'原辅料准备':'PREP','解冻与净制':'CLEAN','分切与规格化':'CUT','调味与腌制':'MARINATE',
    '热加工':'COOK','冷却与冻结':'CHILL','分装与包装':'PACK','检测与入库':'RELEASE'} as Record<string,string>)[name] || '';
}
