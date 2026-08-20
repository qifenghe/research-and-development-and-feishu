<template>
  <a-modal
    :open="open"
    title="正式提交工艺版本"
    :confirm-loading="submitting"
    :ok-button-props="{ disabled: !canSubmit }"
    ok-text="确认提交"
    @cancel="emit('update:open', false)"
    @ok="submit"
  >
    <a-spin :spinning="loading">
      <p class="intro">提交后将冻结当前工艺、配方和 SOP 的来源版本，不能直接覆盖。</p>
      <a-alert v-if="check && !check.ready" type="error" show-icon message="当前工艺尚不能正式提交" style="margin-bottom:10px" />
      <a-alert v-if="error" type="error" show-icon :message="error" style="margin-bottom:10px" />
      <div v-if="check?.errors.length" class="issues error-list"><b>需处理</b><ul><li v-for="issue in check.errors" :key="`${issue.code}-${issue.majorSequence}-${issue.stepSequence}`">{{ issue.message }}</li></ul></div>
      <div v-if="check?.warnings.length" class="issues"><b>提醒</b><ul><li v-for="issue in check.warnings" :key="`${issue.code}-${issue.majorSequence}-${issue.stepSequence}`">{{ issue.message }}</li></ul></div>
      <section class="summary"><b>配方摘要</b><span>{{ recipe.length }} 项外部物料 · {{ totalWeight.toFixed(3) }} kg</span><div v-for="line in recipe" :key="line.materialName" class="line"><span>{{ line.materialName }}</span><span>{{ line.weightKg.toFixed(3) }} kg</span></div></section>
      <section class="yield-summary"><b>大工序得率</b><div v-for="major in plan.majorProcesses" :key="major.key"><span>{{ major.sequence }}. {{ major.processName }}</span><b>{{ percent(yieldOf(major).mainYieldPercent) }}</b></div><footer>最终得率 <b>{{ percent(finalYield) }}</b></footer></section>
      <section v-if="requiresReason" class="differences">
        <b>相对来源正式版本的差异</b>
        <a-empty v-if="!differences.length" :image="false" description="工艺内容无差异" />
        <div v-for="item in differences" :key="`${item.type}-${item.path}-${item.before}-${item.after}`" class="difference-line">
          <a-tag :color="item.type === 'ADDED' ? 'green' : item.type === 'REMOVED' ? 'red' : 'blue'">{{ differenceType(item.type) }}</a-tag>
          <span><b>{{ item.path }}</b><small v-if="item.type === 'CHANGED'">{{ item.before }} → {{ item.after }}</small><small v-else>{{ item.before || item.after }}</small></span>
        </div>
      </section>
      <a-form layout="vertical" style="margin-top:12px">
        <a-form-item v-if="requiresReason" label="变更原因（修订版必填）"><a-textarea v-model:value="changeReason" :rows="2" placeholder="说明本次修改的原因" /></a-form-item>
        <a-checkbox v-if="requiresReason" v-model:checked="diffConfirmed">我已核对版本差异</a-checkbox>
        <a-checkbox v-model:checked="confirmed">我已核对配方、工艺得率和关键控制点，并确认生成正式版本</a-checkbox>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { message } from "ant-design-vue";
import { aggregateProcessRecipe, calculateBatchYield, calculateMajorProcessYield, type ProcessPlanDraft, type ProcessRevision, type ProcessSubmissionPreview } from "@rnd/shared";
import { api } from "../../services/api";
import { diffProcessPlans, type ProcessPlanDifferenceType } from "./processPlanDiff";
import { RequestGeneration } from "./requestGeneration";

const props = defineProps<{ open: boolean; formId?: string; plan: ProcessPlanDraft; ready?: boolean }>();
const emit = defineEmits<{ "update:open": [value: boolean]; submitted: [revision: ProcessRevision] }>();
const loading = ref(false);
const submitting = ref(false);
const error = ref("");
const check = ref<ProcessSubmissionPreview | null>(null);
const confirmed = ref(false);
const diffConfirmed = ref(false);
const changeReason = ref("");
const sourceRevision = ref<ProcessRevision | null>(null);
const checkBinding = ref("");
const requests = new RequestGeneration();
const recipe = computed(() => aggregateProcessRecipe(props.plan));
const totalWeight = computed(() => recipe.value.reduce((sum, item) => sum + item.weightKg, 0));
const finalYield = computed(() => calculateBatchYield(props.plan));
const requiresReason = computed(() => Boolean(props.plan.sourceRevisionId));
const binding = computed(() => `${props.formId || ""}:${props.plan.versionNo}`);
const differences = computed(() => sourceRevision.value ? diffProcessPlans(sourceRevision.value.snapshot, props.plan) : []);
const canSubmit = computed(() => Boolean(
  props.ready !== false
  && props.formId
  && !loading.value
  && !submitting.value
  && check.value?.ready
  && checkBinding.value === binding.value
  && confirmed.value
  && (!requiresReason.value || sourceRevision.value && diffConfirmed.value && changeReason.value.trim()),
));

watch([() => props.open, () => props.formId, () => props.plan.versionNo], ([open]) => {
  requests.invalidate();
  clearState();
  if (open) void loadCheck();
}, { immediate: true });

function clearState() {
  check.value = null;
  sourceRevision.value = null;
  checkBinding.value = "";
  error.value = "";
  confirmed.value = false;
  diffConfirmed.value = false;
  changeReason.value = "";
  loading.value = false;
  submitting.value = false;
}

async function loadCheck() {
  const formId = props.formId;
  if (!formId) { error.value = "请先保存打样草稿后再提交工艺版本"; return; }
  const versionNo = props.plan.versionNo;
  const sourceRevisionId = props.plan.sourceRevisionId;
  const generation = requests.next();
  loading.value = true;
  try {
    const [value, source] = await Promise.all([
      api.task.getProcessSubmissionCheck(formId),
      sourceRevisionId ? api.task.getProcessRevision(formId, sourceRevisionId) : Promise.resolve(null),
    ]);
    if (!requests.isCurrent(generation) || formId !== props.formId || versionNo !== props.plan.versionNo) return;
    check.value = value;
    sourceRevision.value = source;
    checkBinding.value = `${formId}:${versionNo}`;
  } catch (cause) {
    if (requests.isCurrent(generation) && formId === props.formId && versionNo === props.plan.versionNo) {
      check.value = null;
      sourceRevision.value = null;
      checkBinding.value = "";
      error.value = cause instanceof Error ? cause.message : "无法加载提交检查或来源版本";
    }
  } finally {
    if (requests.isCurrent(generation)) loading.value = false;
  }
}

async function submit() {
  const formId = props.formId;
  if (!formId || !canSubmit.value) return;
  const generation = requests.next();
  submitting.value = true;
  error.value = "";
  try {
    const revision = await api.task.submitProcessPlan(formId, { versionNo: props.plan.versionNo, confirmed: confirmed.value, changeReason: changeReason.value.trim() || undefined });
    if (!requests.isCurrent(generation) || formId !== props.formId) return;
    message.success(`已生成正式版本 R${revision.revisionNo}`);
    emit("submitted", revision);
    emit("update:open", false);
  } catch (cause) {
    if (requests.isCurrent(generation)) {
      check.value = null;
      checkBinding.value = "";
      confirmed.value = false;
      diffConfirmed.value = false;
      error.value = cause instanceof Error ? cause.message : "正式提交失败";
    }
  } finally {
    if (requests.isCurrent(generation)) submitting.value = false;
  }
}

function yieldOf(major: ProcessPlanDraft["majorProcesses"][number]) { return calculateMajorProcessYield(major); }
function percent(value: number | null | undefined) { return value == null ? "待补充" : `${value.toFixed(2)}%`; }
function differenceType(value: ProcessPlanDifferenceType) { return value === "ADDED" ? "新增" : value === "REMOVED" ? "删除" : "修改"; }
</script>

<style scoped>
.intro { color: #595959; }
.issues, .summary, .yield-summary, .differences { margin-top: 10px; padding: 10px; border: 1px solid #e5e6eb; border-radius: 8px; background: #fafafa; }
.error-list { border-color: #ffccc7; background: #fff2f0; }
.issues ul { margin: 5px 0 0; padding-left: 18px; color: #595959; }
.summary > b, .summary > span { display: block; }
.summary > span { color: #86909c; font-size: 12px; margin: 3px 0 6px; }
.line, .yield-summary > div, .yield-summary footer { display: flex; justify-content: space-between; padding: 3px 0; font-size: 13px; }
.yield-summary footer { margin-top: 4px; border-top: 1px solid #e5e6eb; color: #1677ff; font-size: 14px; }
.differences { max-height: 220px; overflow: auto; }
.difference-line { display: flex; align-items: start; gap: 6px; margin-top: 7px; }
.difference-line span, .difference-line small { display: block; }
.difference-line small { color: #595959; font-weight: 400; }
</style>
