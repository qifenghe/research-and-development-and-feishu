import type { StepParameterDraft } from "../../../../../packages/shared/src/step-parameters.ts";

type Field = [name: string, unit?: string];
const fields: Record<string, Field[]> = {
  "切块": [["块长","mm"],["块宽","mm"],["块高","mm"],["尺寸允许偏差","mm"],["切割方式"]],
  "煮制": [["介质温度","℃"],["产品中心温度","℃"],["保温时间","min"],["计时起点"],["加热方式"],["结束判断"]],
  "煎炒": [["介质温度","℃"],["操作时长","min"],["翻动/搅拌要求"],["结束判断"]],
  "腌制": [["腌制时长","min"],["物料温度","℃"],["环境温度","℃"],["翻拌频次"],["操作说明"]],
  "滚揉": [["滚筒转速","rpm"],["真空压力","MPa"],["运行时间","min"],["暂停时间","min"],["循环次数","次"],["含暂停总历时","min"],["正反转模式"],["进料温度","℃"],["出料温度","℃"],["单批装载量","kg"]],
  "搅拌": [["搅拌轴转速","rpm"],["刀轴转速","rpm"],["操作时长","min"],["投料顺序"],["出料温度","℃"]],
  "蒸煮": [["介质温度","℃"],["产品中心温度","℃"],["升温时间","min"],["保温时间","min"],["计时起点"],["设备压力","MPa"],["加料时机"]],
  "冷却": [["环境温度","℃"],["产品中心温度","℃"],["操作时长","min"],["装盘厚度","mm"],["风速","m/s"],["结束判断"]],
  "包装": [["单份净重","g"],["净重允许偏差","g"],["封口时间","s"],["封口温度","℃"],["真空压力","MPa"],["检验要求"]],
};
const aliases: Record<string,string> = {分切:"切块",切割:"切块",修割:"切块",焯水:"煮制",煎制:"煎炒",炒制:"煎炒",油炸:"煎炒",斩拌:"搅拌",焖炖:"蒸煮",速冻:"冷却",冻结:"冷却",分装:"包装",封口:"包装"};
export const parameterOperations = Object.keys(fields);
let counter=0;
export function parameterKey() { return `parameter-${Date.now()}-${++counter}-${Math.random().toString(36).slice(2,8)}`; }
export function parameterTemplates(operation: string): StepParameterDraft[] {
  return (fields[aliases[operation] || operation] || []).map(([name,unit]) => ({key:parameterKey(),name,valueType:unit?"NUMBER":"TEXT",unit,showInSummary:false}));
}
export function summaryParameters(parameters: StepParameterDraft[]) { return parameters.filter(p => p.showInSummary).slice(0,3); }
