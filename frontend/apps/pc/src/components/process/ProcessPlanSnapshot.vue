<template>
  <section class="snapshot">
    <a-descriptions size="small" :column="3" bordered>
      <a-descriptions-item label="方案状态">{{ plan.status }}</a-descriptions-item>
      <a-descriptions-item label="方案版本">{{ plan.versionNo }}</a-descriptions-item>
      <a-descriptions-item label="物料平衡允许差">{{ kg(plan.balanceToleranceKg) }}</a-descriptions-item>
      <a-descriptions-item label="汇总配方重量">{{ kg(recipeWeight) }}</a-descriptions-item>
      <a-descriptions-item label="最终得率">{{ percent(finalYield) }}</a-descriptions-item>
      <a-descriptions-item label="来源版本">{{ display(plan.sourceRevisionId) }}</a-descriptions-item>
    </a-descriptions>

    <article v-for="major in plan.majorProcesses" :key="major.key" class="major">
      <header>
        <div>
          <h3>{{ major.sequence }}. {{ major.processName || "未命名大工序" }}</h3>
          <small>代码 {{ display(major.processCode) }} · 得率口径 {{ major.yieldBasis }}</small>
        </div>
        <b>大工序得率 {{ percent(majorYield(major).mainYieldPercent) }}</b>
      </header>
      <dl class="detail-grid">
        <dt>工序说明</dt><dd>{{ display(major.description) }}</dd>
        <dt>差异说明/备注</dt><dd>{{ display(major.remark) }}</dd>
        <dt>主料投入</dt><dd>{{ kg(majorYield(major).primaryInputWeightKg) }}</dd>
        <dt>总投入</dt><dd>{{ kg(majorYield(major).totalInputWeightKg) }}</dd>
        <dt>合格产出</dt><dd>{{ kg(majorYield(major).qualifiedOutputWeightKg) }}</dd>
        <dt>回收率</dt><dd>{{ percent(majorYield(major).recoveryPercent) }}</dd>
        <dt>物料平衡差</dt><dd>{{ kg(majorYield(major).balanceDifferenceKg) }}</dd>
      </dl>

      <section v-if="major.inputs.length || major.outputs.length" class="legacy">
        <h4>旧版兼容字段</h4>
        <div v-for="input in major.inputs" :key="input.key" class="row">
          <b>投入 {{ input.sequence }}</b>
          <span>角色 {{ input.inputRole }}</span><span>编码 {{ display(input.materialCode) }}</span>
          <span>名称 {{ display(input.materialName) }}</span><span>重量 {{ kg(input.weightKg) }}</span>
          <span>来源投料 ID {{ display(input.sourceStepMaterialId) }}</span>
        </div>
        <div v-for="output in major.outputs" :key="output.key" class="row">
          <b>产出 {{ output.sequence }}</b><span>类型 {{ output.outputType }}</span>
          <span>重量 {{ kg(output.weightKg) }}</span><span>备注 {{ display(output.remark) }}</span>
        </div>
      </section>

      <section v-for="step in major.steps" :key="step.key" class="step">
        <header>
          <div><h4>{{ major.sequence }}.{{ step.sequence }} {{ step.stepName || "未命名步骤" }}</h4><small>代码 {{ display(step.stepCode) }} · 类型 {{ step.stepType }}</small></div>
          <b>步骤得率 {{ percent(stepYield(step).mainYieldPercent) }}</b>
        </header>
        <dl class="detail-grid">
          <dt>参数 1</dt><dd>{{ parameter(step.parameter1Name, step.parameter1Value, step.parameter1Unit) }}</dd>
          <dt>参数 2</dt><dd>{{ parameter(step.parameter2Name, step.parameter2Value, step.parameter2Unit) }}</dd>
          <dt>设备</dt><dd>{{ display(step.equipment) }}</dd>
          <dt>操作要求</dt><dd>{{ display(step.instruction) }}</dd>
          <dt>步骤备注</dt><dd>{{ display(step.remark) }}</dd>
        </dl>

        <h5>投料</h5>
        <a-empty v-if="!step.materials.length" :image="false" description="无投料" />
        <div v-for="material in step.materials" :key="material.key" class="row material">
          <b>{{ material.sequence }}. {{ material.materialName || "未命名投料" }}</b>
          <span>来源 {{ material.sourceType || "EXTERNAL" }}</span><span>角色 {{ material.materialRole }}</span>
          <span>配方物料 ID {{ display(material.formulaMaterialId) }}</span><span>编码 {{ display(material.materialCode) }}</span>
          <span>记录 ID {{ display(material.id) }}</span><span>来源产出 ID {{ display(material.sourceStepOutputId) }}</span>
          <span>重量 {{ kg(material.weightKg) }}</span><span>状态 {{ material.materialState }}</span><span>备注 {{ display(material.remark) }}</span>
        </div>

        <h5>产出</h5>
        <a-empty v-if="!step.outputs?.length" :image="false" description="无产出" />
        <div v-for="output in step.outputs || []" :key="output.key" class="row output">
          <b>{{ output.sequence }}. {{ output.outputName || "未命名产出" }}</b>
          <span>类型 {{ output.outputType }}</span><span>记录 ID {{ display(output.id) }}</span>
          <span>重量 {{ kg(output.weightKg) }}</span><span>状态 {{ output.materialState }}</span>
          <span>主产出 {{ yesNo(output.primaryOutput) }}</span><span>继续流转 {{ yesNo(output.continueFlow) }}</span>
          <span>备注 {{ display(output.remark) }}</span>
        </div>

        <h5>关键控制点</h5>
        <a-empty v-if="!step.controlPoints?.length" :image="false" description="无关键控制点" />
        <article v-for="point in step.controlPoints || []" :key="point.key" class="control">
          <header><b>{{ point.sequence }}. {{ point.itemName || "未命名控制点" }}</b><a-tag>{{ point.importance }}</a-tag></header>
          <dl class="detail-grid">
            <dt>控制类型</dt><dd>{{ display(point.controlType) }}</dd>
            <dt>目标/上下限</dt><dd>{{ limit(point.targetValue, point.lowerLimit, point.upperLimit, point.unit) }}</dd>
            <dt>方法</dt><dd>{{ display(point.method) }}</dd>
            <dt>工具</dt><dd>{{ display(point.measurementTool) }}</dd>
            <dt>频次</dt><dd>{{ display(point.frequency) }}</dd>
            <dt>依据/备注</dt><dd>{{ display(point.basisOrRemark) }}</dd>
            <dt>偏差处理要求</dt><dd>{{ display(point.deviationAction) }}</dd>
            <dt>偏差已闭环</dt><dd>{{ yesNo(point.resolved) }}</dd>
            <dt>确认人</dt><dd>{{ display(point.confirmedBy) }}</dd>
            <dt>确认时间</dt><dd>{{ display(point.confirmedAt) }}</dd>
          </dl>
          <div v-for="measurement in point.measurements" :key="measurement.key" class="measurement">
            <b>实测 {{ measurement.sequence }}</b><span>值 {{ display(measurement.measuredValue) }} {{ display(point.unit) }}</span>
            <span>时间 {{ display(measurement.measuredAt) }}</span><span>判定 {{ result(measurement.result) }}</span>
            <span>偏差处理 {{ display(measurement.deviationAction) }}</span><span>复测 {{ result(measurement.retestResult) }}</span>
            <span>备注 {{ display(measurement.remark) }}</span>
          </div>
        </article>
      </section>
    </article>

    <article class="major">
      <h3>汇总配方</h3>
      <div v-for="line in recipe" :key="`${line.formulaMaterialId}-${line.materialCode}-${line.materialName}`" class="recipe-line">
        <span>{{ line.materialName }} · ID {{ display(line.formulaMaterialId) }} · 编码 {{ display(line.materialCode) }}</span>
        <b>{{ kg(line.weightKg) }} · {{ percent(line.ratioPercent) }}</b>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import {
  aggregateProcessRecipe,
  calculateBatchYield,
  calculateMajorProcessYield,
  calculateMinorStepYield,
  type MajorProcessDraft,
  type MinorProcessStepDraft,
  type ProcessPlanDraft,
} from "@rnd/shared";

const props = defineProps<{ plan: ProcessPlanDraft }>();
const recipe = computed(() => aggregateProcessRecipe(props.plan));
const recipeWeight = computed(() => recipe.value.reduce((sum, line) => sum + line.weightKg, 0));
const finalYield = computed(() => calculateBatchYield(props.plan));

function majorYield(major: MajorProcessDraft) { return calculateMajorProcessYield(major); }
function stepYield(step: MinorProcessStepDraft) { return calculateMinorStepYield(step); }
function display(value: unknown) { return value == null || value === "" ? "-" : String(value); }
function kg(value: number | null | undefined) { return value == null ? "-" : `${Number(value).toFixed(3)} kg`; }
function percent(value: number | null | undefined) { return value == null ? "待补充" : `${Number(value).toFixed(2)}%`; }
function yesNo(value: boolean | undefined) { return value ? "是" : "否"; }
function parameter(name?: string, value?: string, unit?: string) { return [name, value, unit].filter(Boolean).join(" ") || "-"; }
function limit(target?: number, lower?: number, upper?: number, unit?: string) { return `目标 ${display(target)}，下限 ${display(lower)}，上限 ${display(upper)} ${display(unit)}`; }
function result(value?: string) { return value === "PASS" ? "合格" : value === "FAIL" ? "不合格" : value === "PENDING" ? "待判定" : "-"; }
</script>

<style scoped>
.snapshot { display: grid; gap: 12px; }
.major, .step, .legacy, .control { padding: 12px; border: 1px solid #e5e6eb; border-radius: 10px; }
.major > header, .step > header, .control > header { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
h3, h4, h5 { margin: 0 0 8px; }
small { color: #86909c; }
.detail-grid { display: grid; grid-template-columns: 120px minmax(0, 1fr) 120px minmax(0, 1fr); margin: 8px 0 0; }
.detail-grid dt, .detail-grid dd { margin: 0; padding: 5px 7px; border-top: 1px solid #f0f0f0; font-size: 12px; }
.detail-grid dt { color: #86909c; }
.step, .legacy { margin-top: 10px; background: #fafafa; }
.row, .measurement { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 5px; margin-top: 6px; padding: 8px; border-radius: 6px; background: #fff; font-size: 12px; }
.material, .output { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.control { margin-top: 7px; background: #fff; }
.measurement { background: #f5f7fa; }
.recipe-line { display: flex; justify-content: space-between; gap: 10px; padding: 5px 0; border-top: 1px solid #f0f0f0; font-size: 12px; }
@media (max-width: 760px) {
  .detail-grid, .row, .measurement, .material, .output { grid-template-columns: 1fr; }
}
</style>
