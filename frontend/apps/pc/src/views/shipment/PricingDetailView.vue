<template>
  <div>
    <a-page-header :title="detail?.pricingFile.fileName ?? '核价文件详情'" sub-title="确认包装后生成正式核价文件；审核通过后自动移交财务" />
    <a-spin :spinning="loading">
      <a-row :gutter="16" v-if="detail">
        <a-col :span="16">
          <a-card v-if="isPackagingDraft" title="包装确认" class="page-card">
            <a-alert
              type="info"
              show-icon
              message="实验单中的原辅料和成品出成将自动带入正式核价文件"
              description="内袋标签、外箱标签已按产品名称自动带入且不显示编码。请补充或调整实际包装，确认后生成正式 Excel。"
              style="margin-bottom: 16px"
            />
            <a-alert v-if="!canEditPackaging" type="warning" show-icon message="核价草稿已生成，等待研发总监或产品负责人确认包装。" style="margin-bottom: 16px" />
            <a-table :columns="packagingColumns" :data-source="packagingItems" :pagination="false" row-key="clientKey" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'source'">
                  <a-tag :color="record.source === 'SYSTEM_LABEL' ? 'blue' : record.source === 'MANUAL' ? 'orange' : 'default'">
                    {{ sourceLabel(record.source) }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'materialCode'">
                  <a-input v-model:value="record.materialCode" :disabled="!canEditPackaging || record.source === 'SYSTEM_LABEL'" placeholder="无编码可留空" />
                </template>
                <template v-else-if="column.key === 'materialName'">
                  <a-input v-model:value="record.materialName" :disabled="!canEditPackaging || record.source === 'SYSTEM_LABEL'" placeholder="包装名称" />
                </template>
                <template v-else-if="column.key === 'quantity'">
                  <a-input-number v-model:value="record.quantity" :disabled="!canEditPackaging" :min="0.001" :precision="3" style="width: 100%" />
                </template>
                <template v-else-if="column.key === 'packageSpec'">
                  <a-input v-model:value="record.packageSpec" :disabled="!canEditPackaging" placeholder="如 500g/袋" />
                </template>
                <template v-else-if="column.key === 'quantityUnit'"><a-input :value="record.quantityUnit ?? ''" @update:value="record.quantityUnit = $event" :disabled="!canEditPackaging" placeholder="明确填写，如 个、张、米" /></template>
                <template v-else-if="column.key === 'remark'">
                  <a-input v-model:value="record.remark" :disabled="!canEditPackaging" placeholder="可选" />
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-button v-if="canEditPackaging && record.source !== 'SYSTEM_LABEL'" type="link" danger @click="removePackagingItem(index)">删除</a-button>
                </template>
              </template>
            </a-table>
            <a-space v-if="canEditPackaging" style="margin-top: 16px">
              <a-button @click="addPackagingItem">添加包装</a-button>
              <a-button type="primary" :loading="confirmingPackaging" @click="confirmPackaging">确认包装并生成核价文件</a-button>
            </a-space>
          </a-card>

          <a-card v-for="group in resolvedGroups" :key="group.title" :title="group.title" class="page-card">
            <a-descriptions bordered size="small" :column="2">
              <a-descriptions-item v-for="field in group.fields" :key="field.label" :label="field.label">
                {{ field.value }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card title="操作">
            <a-space direction="vertical" style="width: 100%">
              <a-button v-if="canDownload" block :loading="downloading" @click="download">下载核价文件</a-button>
              <a-button v-if="canDownload" block :loading="exporting" @click="exportPricing">导出核价报表</a-button>
              <template v-if="canReview">
                <a-textarea v-model:value="reviewComment" placeholder="审核意见；退回时必填原因" :rows="3" />
                <a-button type="primary" block @click="review('APPROVE')">审核通过并移交财务</a-button>
                <a-button danger block @click="review('REJECT')">退回核价</a-button>
              </template>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import { resolvePricingDetailFields, type PricingFileDetailView, type PricingPackagingItem } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

type EditablePackagingItem = PricingPackagingItem & { clientKey: string };

const route = useRoute();
const auth = useAuthStore();
const loading = ref(false);
const downloading = ref(false);
const exporting = ref(false);
const confirmingPackaging = ref(false);
const detail = ref<PricingFileDetailView | null>(null);
const packagingItems = ref<EditablePackagingItem[]>([]);
const reviewComment = ref("");
const resolvedGroups = computed(() => (detail.value ? resolvePricingDetailFields(detail.value) : []));
const isPackagingDraft = computed(() => detail.value?.pricingFile.status === "DRAFT_PACKAGING");
const canEditPackaging = computed(() => ["RND_DIRECTOR", "RND_ENGINEER", "SUPER_ADMIN"].includes(auth.role ?? ""));
const canDownload = computed(() => !isPackagingDraft.value && [
  "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", "ADMIN", "SYSTEM_ADMIN", "SUPER_ADMIN",
].includes(auth.role ?? ""));
const canReview = computed(() => (auth.role === "RND_DIRECTOR" || auth.role === "RND_ENGINEER")
  && detail.value?.pricingFile.status === "PENDING_PRICING_REVIEW");

const packagingColumns = [
  { title: "来源", key: "source", width: 96 },
  { title: "物料编码", key: "materialCode", width: 130 },
  { title: "包装名称", key: "materialName", width: 190 },
  { title: "数量", key: "quantity", width: 110 },
  { title: "数量单位", key: "quantityUnit", width: 150 },
  { title: "规格", key: "packageSpec", width: 130 },
  { title: "备注", key: "remark" },
  { title: "操作", key: "action", width: 70 },
];

async function load() {
  loading.value = true;
  try {
    detail.value = await api.shipment.pricingDetail(String(route.params.id));
    packagingItems.value = (detail.value.packagingItems ?? []).map((item, index) => ({ ...item, clientKey: item.id ?? `packaging-${index}` }));
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载核价文件失败");
  } finally {
    loading.value = false;
  }
}

function sourceLabel(source: PricingPackagingItem["source"]) {
  return { TEMPLATE: "模板", SYSTEM_LABEL: "自动标签", MANUAL: "手工添加" }[source];
}

function addPackagingItem() {
  packagingItems.value.push({
    clientKey: `manual-${Date.now()}`,
    source: "MANUAL",
    materialName: "",
    quantity: 1,
    packageSpec: "",
    remark: "",
  });
}

function removePackagingItem(index: number) {
  packagingItems.value.splice(index, 1);
}

async function confirmPackaging() {
  if (!canEditPackaging.value) return message.warning("仅研发总监或该产品负责人可确认包装");
  if (!packagingItems.value.length) return message.warning("请至少保留一项包装");
  if (packagingItems.value.some((item) => !item.materialName.trim() || !Number(item.quantity) || Number(item.quantity) <= 0)) {
    return message.warning("请补全每项包装的名称和数量");
  }
  confirmingPackaging.value = true;
  try {
    await api.shipment.confirmPricingPackaging(String(route.params.id), packagingItems.value.map(({ clientKey, ...item }, index) => ({
      ...item,
      sequence: index + 1,
      materialCode: item.source === "SYSTEM_LABEL" ? undefined : item.materialCode?.trim() || undefined,
      materialName: item.materialName.trim(),
      packageSpec: item.packageSpec?.trim() || undefined,
      remark: item.remark?.trim() || undefined,
      confirmationStatus: "CONFIRMED",
    })));
    message.success("包装已确认，正式核价文件已生成，等待核价审核");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "确认包装失败");
  } finally {
    confirmingPackaging.value = false;
  }
}

async function download() {
  downloading.value = true;
  try {
    const blob = await api.shipment.downloadPricingFile(String(route.params.id));
    downloadBlob(blob, detail.value?.pricingFile.fileName ?? "pricing.xlsx");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  } finally {
    downloading.value = false;
  }
}

async function exportPricing() {
  exporting.value = true;
  try {
    const blob = await api.report.exportPricingFile(String(route.params.id));
    downloadBlob(blob, detail.value?.pricingFile.fileName ?? "pricing.xlsx");
    message.success("核价文件已导出");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "导出失败");
  } finally {
    exporting.value = false;
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

async function review(decision: "APPROVE" | "REJECT") {
  try {
    await api.shipment.reviewPricingFile(String(route.params.id), { decision, comment: reviewComment.value.trim() || undefined });
    message.success(decision === "APPROVE" ? "审核通过，已自动移交财务" : "核价已退回");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "审核失败");
  }
}

onMounted(load);
</script>
