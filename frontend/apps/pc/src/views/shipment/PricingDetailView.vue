<template>
  <div>
    <a-page-header :title="detail?.pricingFile.fileName ?? '核价文件详情'" sub-title="按Excel模板生成版本化核价文件，并通知财务" />
    <a-spin :spinning="loading">
      <a-row :gutter="16" v-if="detail">
        <a-col :span="16">
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
              <a-button block :loading="exporting" @click="exportPricing">导出核价报表</a-button>
              <template v-if="canReview">
                <a-textarea v-model:value="reviewComment" placeholder="审核意见；退回时必填原因" :rows="3" />
                <a-button type="primary" block @click="review('APPROVE')">审核通过</a-button>
                <a-button danger block @click="review('REJECT')">退回核价</a-button>
              </template>
              <template v-if="canNotifyFinance">
                <a-input v-model:value="recipientName" placeholder="财务接收人" />
                <a-button type="primary" block @click="notifyFinance">通知财务核价</a-button>
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
import { resolvePricingDetailFields, type PricingFileDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const route = useRoute();
const auth = useAuthStore();
const loading = ref(false);
const downloading = ref(false);
const exporting = ref(false);
const detail = ref<PricingFileDetailView | null>(null);
const recipientName = ref("财务部");
const reviewComment = ref("");
const resolvedGroups = computed(() => (detail.value ? resolvePricingDetailFields(detail.value) : []));
const canDownload = computed(() => [
  "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", "ADMIN", "SYSTEM_ADMIN",
].includes(auth.role ?? ""));
const canReview = computed(() => (auth.role === "RND_DIRECTOR" || auth.role === "RND_ENGINEER")
  && detail.value?.pricingFile.status === "PENDING_PRICING_REVIEW");
const canNotifyFinance = computed(() => auth.role === "RND_ASSISTANT"
  && detail.value?.pricingFile.status === "PRICING_APPROVED");

async function load() {
  loading.value = true;
  try {
    detail.value = await api.shipment.pricingDetail(String(route.params.id));
  } finally {
    loading.value = false;
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

async function notifyFinance() {
  try {
    await api.shipment.notifyFinance(String(route.params.id), recipientName.value.trim());
    message.success("已通知财务");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "通知失败");
  }
}

async function review(decision: "APPROVE" | "REJECT") {
  try {
    await api.shipment.reviewPricingFile(String(route.params.id), {
      decision,
      comment: reviewComment.value.trim() || undefined,
    });
    message.success(decision === "APPROVE" ? "核价审核通过" : "核价已退回");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "审核失败");
  }
}

onMounted(load);
</script>
