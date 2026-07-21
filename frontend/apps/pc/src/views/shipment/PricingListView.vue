<template>
  <div>
    <a-page-header title="核价文件列表" sub-title="可从样品完成后单独生成核价，也可从客户通过后生成核价" />
    <a-card v-if="canGeneratePricing" title="待生成核价" style="margin-bottom: 16px">
      <a-table :columns="readyColumns" :data-source="readyRows" row-key="versionId" :loading="loadingReady" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="primary" size="small" :loading="generatingId === record.versionId" @click="generate(record.versionId)">
              生成核价
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button size="small" @click="router.push(`/pricing/${record.id}`)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { PricingFileRecord, PricingReadyVersion } from "@rnd/shared";
import { api } from "../../services/api";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const rows = ref<PricingFileRecord[]>([]);
const readyRows = ref<PricingReadyVersion[]>([]);
const loadingReady = ref(false);
const generatingId = ref("");
const canGeneratePricing = computed(() => auth.role === "RND_ASSISTANT" || auth.role === "RND_DIRECTOR");

const readyColumns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "操作", key: "action" },
];

const columns = [
  { title: "文件名", dataIndex: "fileName", key: "fileName" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  loadingReady.value = true;
  try {
    rows.value = await api.shipment.pricingFiles() as PricingFileRecord[];
    readyRows.value = canGeneratePricing.value ? await api.shipment.pricingReadyVersions() : [];
  } finally {
    loading.value = false;
    loadingReady.value = false;
  }
}

async function generate(versionId: string) {
  generatingId.value = versionId;
  try {
    const file = await api.shipment.createPricingFile(versionId);
    message.success(`已生成 ${file.pricingVersion}`);
    await router.push(`/pricing/${file.id}`);
  } finally {
    generatingId.value = "";
  }
}

onMounted(load);
</script>
