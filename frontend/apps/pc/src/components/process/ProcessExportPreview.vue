<template>
  <a-button size="small" :disabled="disabled || !formId" @click="openPreview">检查与研发预览</a-button>
  <a-modal v-model:open="open" :title="`研发预览 · ${sourceLabel}`" :footer="null" width="680">
    <a-alert type="info" show-icon message="仅预览已保存版本，不提交、不归档、不改变审批状态。待补充内容会标记在预览文件中。" />
    <a-alert v-if="error" type="error" :message="error" />
    <div v-for="type in types" :key="type.value" class="preview-type">
      <a-space><b>{{ type.label }}</b><span>{{ checks[type.value]?.ready ? "检查通过" : checks[type.value] ? "待完善" : "检查中…" }}</span><a-button size="small" :loading="downloading === type.value" :disabled="loading || Boolean(downloading) || disabled" @click="download(type.value)">下载研发预览</a-button></a-space>
      <ul><li v-for="(issue,index) in checks[type.value]?.issues || []" :key="`${issue.code}-${issue.path}-${index}`">
        <a-button v-if="issue.majorSequence" type="link" size="small" @click="locate(issue)">工序 {{ issue.majorSequence }}{{ issue.stepSequence ? ` / 步骤 ${issue.stepSequence}` : "" }}</a-button>{{ issue.message }}
      </li></ul>
    </div>
  </a-modal>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from "vue";
import { exportApi, type ExportType, type ExportCheck, type ExportIssue } from "../../services/processExportApi";
import { RequestGeneration } from "./requestGeneration";
const props = defineProps<{ formId?: string; trialId?: string; versionNo: number; sourceLabel: string; disabled?: boolean }>();
const emit = defineEmits<{ locate: [issue: ExportIssue] }>();
const open = ref(false), loading = ref(false), error = ref("");
const checks = ref<Partial<Record<ExportType, ExportCheck>>>({});
const downloading = ref<ExportType>();
const fence = new RequestGeneration();
const types = [{ value: "FORMULA_XLSX" as const, label: "研发配方" }, { value: "SOP_DOCX" as const, label: "研发SOP" }, { value: "PRICING_XLSX" as const, label: "核价基础数据" }];
watch(() => [props.formId, props.trialId, props.versionNo, props.sourceLabel, props.disabled], reset);
onBeforeUnmount(reset);
function reset() { fence.invalidate(); open.value = false; checks.value = {}; downloading.value = undefined; loading.value = false; error.value = ""; }
function scope() { return { formId: props.formId!, trialId: props.trialId, versionNo: props.versionNo }; }
async function openPreview() {
  if (!props.formId || props.disabled) return;
  const saved = scope(), token = fence.next(); open.value = true; loading.value = true; checks.value = {}; error.value = "";
  try {
    const result = await Promise.all(types.map(async type => [type.value, await exportApi.check(saved, type.value)] as const));
    if (fence.isCurrent(token)) checks.value = Object.fromEntries(result);
  } catch (e) { if (fence.isCurrent(token)) error.value = e instanceof Error ? e.message : "检查失败"; }
  finally { if (fence.isCurrent(token)) loading.value = false; }
}
function locate(issue: ExportIssue) { open.value = false; emit("locate", issue); }
async function download(type: ExportType) {
  if (!props.formId || props.disabled || downloading.value) return;
  const saved = scope(), token = fence.capture(), label = props.sourceLabel;
  downloading.value = type; error.value = "";
  try {
    const blob = await exportApi.preview(saved, type);
    if (!fence.isCurrent(token)) return;
    const url = URL.createObjectURL(blob); const link = document.createElement("a");
    const artifactName = types.find(item => item.value === type)?.label || type;
    link.href = url; link.download = `${label}-研发预览-${artifactName}.${type === "SOP_DOCX" ? "docx" : "xlsx"}`;
    document.body.append(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url));
  } catch (e) { if (fence.isCurrent(token)) error.value = e instanceof Error ? e.message : "预览失败"; }
  finally { if (fence.isCurrent(token)) downloading.value = undefined; }
}
</script>
<style scoped>.preview-type { margin-top: 16px; } ul { margin-top: 8px; padding-left: 20px; color: #92400e; } li { margin: 5px 0; }</style>
