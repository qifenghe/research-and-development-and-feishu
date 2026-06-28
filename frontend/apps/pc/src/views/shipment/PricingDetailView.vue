<template>
  <div>
    <a-page-header :title="detail?.pricingFile.fileName ?? '核价文件详情'" sub-title="按Excel模板生成版本化核价文件，并通知财务" />
    <a-spin :spinning="loading">
      <a-row :gutter="16" v-if="detail">
        <a-col :span="16">
          <a-card v-for="group in detail.fieldGroups" :key="group.title" :title="group.title" class="page-card">
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
              <a-button block @click="download">下载核价文件</a-button>
              <a-input v-model:value="recipientName" placeholder="财务接收人" />
              <a-button type="primary" block @click="notifyFinance">通知财务核价</a-button>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import type { PricingFileDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const route = useRoute();
const auth = useAuthStore();
const loading = ref(false);
const detail = ref<PricingFileDetailView | null>(null);
const recipientName = ref("财务部");

async function load() {
  loading.value = true;
  try {
    detail.value = await api.shipment.pricingDetail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
}

async function download() {
  try {
    const blob = await api.shipment.downloadPricingFile(String(route.params.id));
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = detail.value?.pricingFile.fileName ?? "pricing.xlsx";
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  }
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

onMounted(load);
</script>
