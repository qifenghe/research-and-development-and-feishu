<template>
  <section class="output-center">
    <header>
      <div><b>成果输出中心</b><small>所有文件均绑定正式工艺版本</small></div>
      <a-select v-if="revisions.length" :value="selectedRevisionId" size="small" style="width:150px" :options="revisionOptions" @update:value="selectRevision" />
    </header>
    <a-empty v-if="!selectedRevisionId" description="正式提交后可生成配方与 SOP" :image="false" />
    <template v-else>
      <a-alert v-if="loadError" type="error" show-icon :message="loadError" style="margin-bottom:8px" />
      <div v-for="type in artifactTypes" :key="type.value" class="artifact">
        <div>
          <b>{{ type.label }}</b>
          <small>{{ artifactFor(type.value)?.documentVersion || "尚未生成" }} · {{ statusLabel(artifactFor(type.value)?.status) }}</small>
          <small v-if="artifactFor(type.value)?.status === 'FAILED'" class="failure">{{ artifactFor(type.value)?.failureReason || "生成失败，请重试" }}</small>
        </div>
        <a-space>
          <a-button v-if="!readonly" size="small" :loading="generating === type.value" :disabled="Boolean(generating)" @click="generate(type.value)">{{ artifactFor(type.value) ? "重新生成" : "生成" }}</a-button>
          <a-button v-if="artifactFor(type.value)?.status === 'READY'" size="small" type="link" @click="download(artifactFor(type.value)!.id, type.value)">下载</a-button>
        </a-space>
      </div>
      <div class="pricing-wait"><b>生产核价</b><span>等待包装确认后由现有核价流程生成</span></div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { message } from "ant-design-vue";
import type { ProcessArtifact, ProcessArtifactType, ProcessRevisionSummary } from "@rnd/shared";
import { api } from "../../services/api";
import { RequestGeneration } from "./requestGeneration";

const props = defineProps<{ formId?: string; revisions: ProcessRevisionSummary[]; selectedRevisionId?: string; readonly?: boolean }>();
const emit = defineEmits<{ "update:selectedRevisionId": [value: string | undefined] }>();
const artifacts = ref<ProcessArtifact[]>([]);
const generating = ref<ProcessArtifactType>();
const loadError = ref("");
const listGeneration = new RequestGeneration();
const mutationGeneration = new RequestGeneration();
const artifactTypes = [{ label: "标准配方 Formula", value: "FORMULA_XLSX" as const }, { label: "生产 SOP", value: "SOP_DOCX" as const }];
const revisionOptions = computed(() => props.revisions.map(item => ({ label: `正式版本 R${item.revisionNo}`, value: item.id })));

watch(() => props.formId, () => {
  listGeneration.invalidate();
  mutationGeneration.invalidate();
  artifacts.value = [];
  loadError.value = "";
  generating.value = undefined;
  emit("update:selectedRevisionId", undefined);
});
watch(() => props.selectedRevisionId, () => {
  listGeneration.invalidate();
  mutationGeneration.invalidate();
  artifacts.value = [];
  loadError.value = "";
  generating.value = undefined;
  void loadArtifacts();
}, { immediate: true });

function selectRevision(value: string) {
  emit("update:selectedRevisionId", value);
}

async function loadArtifacts() {
  const formId = props.formId;
  const revisionId = props.selectedRevisionId;
  if (!formId || !revisionId) return;
  const generation = listGeneration.next();
  loadError.value = "";
  try {
    const value = await api.task.getProcessArtifacts(formId, revisionId);
    if (!listGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    artifacts.value = value;
  } catch (error) {
    if (!listGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    artifacts.value = [];
    loadError.value = error instanceof Error ? error.message : "无法加载成果文件";
  }
}

function artifactFor(type: ProcessArtifactType) {
  return artifacts.value.find(item => item.artifactType === type);
}

function statusLabel(status?: string) {
  return status === "READY" ? "已就绪" : status === "FAILED" ? "生成失败" : "待生成";
}

async function generate(type: ProcessArtifactType) {
  const formId = props.formId;
  const revisionId = props.selectedRevisionId;
  if (!formId || !revisionId || generating.value) return;
  const generation = mutationGeneration.next();
  generating.value = type;
  try {
    const result = await api.task.generateProcessArtifact(formId, revisionId, type);
    if (!mutationGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    if (result.status === "READY") message.success("文件已生成");
    else message.error(result.failureReason || "文件生成失败");
  } catch (error) {
    if (mutationGeneration.isCurrent(generation) && formId === props.formId && revisionId === props.selectedRevisionId) {
      message.error(error instanceof Error ? error.message : "生成失败");
    }
  } finally {
    if (mutationGeneration.isCurrent(generation) && formId === props.formId && revisionId === props.selectedRevisionId) {
      await loadArtifacts();
      if (mutationGeneration.isCurrent(generation) && formId === props.formId && revisionId === props.selectedRevisionId) {
        generating.value = undefined;
      }
    }
  }
}

async function download(id: string, type: ProcessArtifactType) {
  const formId = props.formId;
  const revisionId = props.selectedRevisionId;
  if (!formId || !revisionId) return;
  try {
    const blob = await api.report.downloadProcessArtifact(formId, revisionId, id);
    if (formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${type === "FORMULA_XLSX" ? "标准配方" : "生产SOP"}-R${props.revisions.find(item => item.id === revisionId)?.revisionNo || ""}.${type === "FORMULA_XLSX" ? "xlsx" : "docx"}`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url));
  } catch (error) {
    if (formId === props.formId && revisionId === props.selectedRevisionId) message.error(error instanceof Error ? error.message : "下载失败");
  }
}
</script>

<style scoped>
.output-center { padding: 12px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; } .output-center header, .artifact, .pricing-wait { display: flex; align-items: center; justify-content: space-between; gap: 12px; } .output-center header { margin-bottom: 8px; } .output-center small, .artifact small { display: block; color: #86909c; font-size: 12px; } .artifact .failure { color: #cf1322; } .artifact, .pricing-wait { padding: 10px 0; border-top: 1px solid #f0f0f0; } .pricing-wait span { color: #86909c; font-size: 12px; }
</style>
