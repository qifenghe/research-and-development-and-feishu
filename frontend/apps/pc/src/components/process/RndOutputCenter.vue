<template>
  <section class="output-center">
    <header>
      <div><b>成果输出中心</b><small>配方、SOP、核价按正式工艺版本归集；旧版文件保留</small></div>
      <a-select v-if="revisions.length" :value="selectedRevisionId" size="small" style="width:150px" :options="revisionOptions" @update:value="selectRevision" />
    </header>
    <a-empty v-if="!selectedRevisionId" description="正式提交后可归集配方、SOP 与核价文件" :image="false" />
    <template v-else>
      <a-alert v-if="loadError" type="error" show-icon :message="loadError" style="margin-bottom:8px" />
      <div class="bundle-status"><span>本版本 {{ completedFileCount }}/3 份已生成{{ completedFileCount === 3 ? ' · 文件齐全' : ' · 尚未齐全' }}</span><a-button size="small" :disabled="Boolean(generating)" @click="loadArtifacts">刷新文件</a-button></div>
      <div v-for="type in artifactTypes" :key="type.value" class="artifact">
        <div>
          <b>{{ type.label }}</b>
          <small>{{ artifactFor(type.value)?.documentVersion || "尚未生成" }} · {{ statusLabel(artifactFor(type.value)?.status) }}</small>
          <small v-if="readyArtifactFor(type.value)">生成时间：{{ time(readyArtifactFor(type.value)!.generatedAt) }}</small>
          <small v-if="type.value==='SOP_XLSX' && readyArtifactFor(type.value)?.artifactType==='SOP_DOCX'">已归档的是历史 Word 文件；重新生成将使用原版 Excel 模板</small>
          <small v-if="artifactFor(type.value)?.status === 'FAILED'" class="failure">{{ artifactFor(type.value)?.failureReason || "生成失败，请重试" }}</small>
          <small v-for="(issue,index) in checks[type.value]?.issues || []" :key="`${issue.code}-${issue.path}-${index}`" class="failure">{{ issue.majorSequence ? `工序 ${issue.majorSequence}${issue.stepSequence ? ` / 步骤 ${issue.stepSequence}` : ""}：` : "" }}{{ issue.message }}</small>
        </div>
        <a-space>
          <a-button v-if="!readonly" size="small" :loading="generating === type.value" :disabled="Boolean(generating) || !checks[type.value]?.ready" @click="generate(type.value)">{{ artifactFor(type.value) ? "重新生成" : "生成" }}</a-button>
          <a-button v-if="readyArtifactFor(type.value)" size="small" type="link" @click="download(readyArtifactFor(type.value)!.id, type.value)">下载已归档版本 {{ readyArtifactFor(type.value)!.documentVersion }}</a-button>
        </a-space>
      </div>
      <div class="artifact pricing-artifact">
        <div><b>核价基础文件</b><small>包装确认及总监审核沿用核价流程，不自动审批</small>
          <small v-if="pricingError" class="failure">{{ pricingError }}</small>
          <small v-else-if="!pricingFiles.length">尚未生成本正式版本的核价文件</small>
          <div v-for="file in pricingFiles" :key="file.id" class="pricing-record">
            <small>{{ file.pricingVersion }} · {{ pricingStatusLabel(file.status) }} · {{ time(file.generatedAt) }}</small>
            <small v-if="file.reviewedBy">审核人：{{ file.reviewedBy }} · {{ time(file.reviewedAt) }}</small>
            <small v-if="file.rejectionReason" class="failure">退回原因：{{ file.rejectionReason }}</small>
            <a-space><a-button size="small" type="link" :href="`/admin/pricing/${file.id}`">查看 / 处理</a-button><a-button v-if="canDownloadPricing(file)" size="small" type="link" @click="downloadPricing(file)">下载已归档 {{ file.pricingVersion }}</a-button></a-space>
          </div>
        </div>
        <a-button size="small" type="link" href="/admin/pricing/list">进入核价列表</a-button>
      </div>
      <details v-if="artifacts.length" class="archive-history"><summary>本正式版本的文件历史（{{ artifacts.length }}）</summary>
        <div v-for="file in artifacts" :key="file.id" class="history-file">
          <span>{{ file.artifactType === 'FORMULA_XLSX' ? '研发配方' : '研发 SOP' }} V{{ file.documentVersion }} · {{ statusLabel(file.status) }} · {{ time(file.generatedAt) }}</span>
          <a-button v-if="file.status === 'READY'" size="small" type="link" @click="download(file.id,file.artifactType)">下载此文件</a-button>
          <small v-else class="failure">{{ file.failureReason || '生成失败，未归档可下载内容' }}</small>
        </div>
      </details>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { message } from "ant-design-vue";
import { PRICING_FILE_STATUS_LABELS, type PricingFileRecord, type ProcessArtifact, type ProcessArtifactType, type ProcessRevisionSummary } from "@rnd/shared";
import { api } from "../../services/api";
import { RequestGeneration } from "./requestGeneration";
import { exportApi, type ExportCheck } from "../../services/processExportApi";
import { sopAmendmentApi } from "../../services/sopAmendmentApi";

const props = defineProps<{ formId?: string; revisions: ProcessRevisionSummary[]; selectedRevisionId?: string; readonly?: boolean; catalogAccess?: boolean }>();
const emit = defineEmits<{ "update:selectedRevisionId": [value: string | undefined] }>();
const artifacts = ref<ProcessArtifact[]>([]);
const pricingFiles = ref<PricingFileRecord[]>([]);
const pricingError = ref("");
const checks = ref<Partial<Record<ProcessArtifactType, ExportCheck>>>({});
const generating = ref<ProcessArtifactType>();
const loadError = ref("");
const listGeneration = new RequestGeneration();
const mutationGeneration = new RequestGeneration();
const artifactTypes = [{ label: "研发配方", value: "FORMULA_XLSX" as const }, { label: "研发 SOP（Excel）", value: "SOP_XLSX" as const }];
const revisionOptions = computed(() => props.revisions.map(item => ({ label: `正式版本 R${item.revisionNo}`, value: item.id })));
const completedFileCount = computed(() => artifactTypes.filter(type => readyArtifactFor(type.value)).length + (pricingFiles.value.some(canDownloadPricing) ? 1 : 0));
function time(value?: string) { return value?.replace('T',' ').slice(0,16) || '—'; }
function pricingStatusLabel(status: string) { return PRICING_FILE_STATUS_LABELS[status] ?? status; }
function canDownloadPricing(file: PricingFileRecord) { return file.processRevisionId === props.selectedRevisionId && file.status !== 'DRAFT_PACKAGING' && (file.contentLength ?? 0) > 0; }

watch(() => props.formId, () => {
  listGeneration.invalidate();
  mutationGeneration.invalidate();
  artifacts.value = [];
  pricingFiles.value = [];
  pricingError.value = "";
  checks.value = {};
  loadError.value = "";
  generating.value = undefined;
  emit("update:selectedRevisionId", undefined);
});
watch(() => props.selectedRevisionId, () => {
  listGeneration.invalidate();
  mutationGeneration.invalidate();
  artifacts.value = [];
  pricingFiles.value = [];
  pricingError.value = "";
  checks.value = {};
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
    const [artifactResult, pricingResult] = await Promise.allSettled([
      props.catalogAccess ? sopAmendmentApi.artifacts(formId, revisionId) : api.task.getProcessArtifacts(formId, revisionId), api.shipment.pricingFiles(),
    ]);
    if (!listGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    artifacts.value = artifactResult.status === 'fulfilled' ? artifactResult.value : [];
    loadError.value = artifactResult.status === 'rejected' ? (artifactResult.reason instanceof Error ? artifactResult.reason.message : '无法加载配方与 SOP') : '';
    pricingFiles.value = pricingResult.status === 'fulfilled' ? (pricingResult.value as PricingFileRecord[]).filter(file => file.processRevisionId === revisionId) : [];
    pricingError.value = pricingResult.status === 'rejected' ? (pricingResult.reason instanceof Error ? pricingResult.reason.message : '无法加载核价文件，请检查权限或稍后重试') : '';
    if (!props.readonly) {
      const results = await Promise.all(artifactTypes.map(async type => [type.value, await exportApi.revisionCheck(formId, revisionId, type.value)] as const));
      if (!listGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
      checks.value = Object.fromEntries(results);
    }
  } catch (error) {
    if (!listGeneration.isCurrent(generation) || formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    checks.value = {};
    loadError.value = error instanceof Error ? error.message : "无法加载成果文件";
  }
}

function artifactFor(type: ProcessArtifactType) {
  return artifacts.value.find(item => item.artifactType === type);
}
function readyArtifactFor(type: ProcessArtifactType) { return artifacts.value.find(item => item.artifactType === type && item.status === "READY") || (type === 'SOP_XLSX' ? artifacts.value.find(item => item.artifactType === 'SOP_DOCX' && item.status === 'READY') : undefined); }

function statusLabel(status?: string) {
  return status === "READY" ? "已就绪" : status === "FAILED" ? "生成失败" : "待生成";
}

async function generate(type: ProcessArtifactType) {
  const formId = props.formId;
  const revisionId = props.selectedRevisionId;
  if (!formId || !revisionId || generating.value || props.readonly || !checks.value[type]?.ready) return;
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
    const blob = await (props.catalogAccess ? sopAmendmentApi.download(formId, revisionId, id) : api.report.downloadProcessArtifact(formId, revisionId, id));
    if (formId !== props.formId || revisionId !== props.selectedRevisionId) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    const fileType=artifacts.value.find(item=>item.id===id)?.artifactType || type;
    link.download = `${type === "FORMULA_XLSX" ? "研发配方" : "研发SOP"}-R${props.revisions.find(item => item.id === revisionId)?.revisionNo || ""}-V${artifacts.value.find(item => item.id === id)?.documentVersion || ""}.${fileType === "SOP_DOCX" ? "docx" : "xlsx"}`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url));
  } catch (error) {
    if (formId === props.formId && revisionId === props.selectedRevisionId) message.error(error instanceof Error ? error.message : "下载失败");
  }
}

async function downloadPricing(file: PricingFileRecord) {
  if (!canDownloadPricing(file)) return;
  const revisionId = props.selectedRevisionId, formId = props.formId;
  try {
    const blob = await api.shipment.downloadPricingFile(file.id);
    if (revisionId !== props.selectedRevisionId || formId !== props.formId) return;
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = file.fileName || `核价-R${props.revisions.find(item => item.id === revisionId)?.revisionNo}-${file.pricingVersion}.xlsx`;
    document.body.append(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url));
  } catch (error) {
    if (revisionId === props.selectedRevisionId && formId === props.formId) message.error(error instanceof Error ? error.message : '核价下载失败');
  }
}
</script>

<style scoped>
.output-center { padding: 12px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; } .output-center header, .artifact, .pricing-wait { display: flex; align-items: center; justify-content: space-between; gap: 12px; } .output-center header { margin-bottom: 8px; } .output-center small, .artifact small { display: block; color: #86909c; font-size: 12px; } .artifact .failure { color: #cf1322; } .artifact, .pricing-wait { padding: 10px 0; border-top: 1px solid #f0f0f0; } .pricing-wait span { color: #86909c; font-size: 12px; }
.bundle-status,.history-file{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.bundle-status{padding:10px 0;color:#245c59}.pricing-record{padding-top:10px}.archive-history{border-top:1px solid #e5e6eb;padding-top:12px}.archive-history summary{cursor:pointer}.history-file{padding:8px 0;font-size:12px}.history-file .failure{color:#cf1322}@media(max-width:700px){.output-center header,.artifact{flex-wrap:wrap}.artifact>div{min-width:0;width:100%}.output-center small{overflow-wrap:anywhere}}
</style>
