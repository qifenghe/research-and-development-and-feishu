<template>
  <div>
    <a-page-header title="寄样反馈列表" sub-title="多个样品同时等待反馈，先列表排队，再进入详情处理">
      <template #extra>
        <a-button v-if="canRecord" type="primary" @click="router.push('/shipment/record')">登记寄样</a-button>
      </template>
    </a-page-header>

    <div v-if="summary.total" class="list-summary-bar">
      <div class="list-summary-bar__item">
        <strong>{{ summary.pending }}</strong><span>待反馈</span>
      </div>
      <div class="list-summary-bar__item">
        <strong>{{ summary.passed }}</strong><span>客户已通过</span>
      </div>
      <div class="list-summary-bar__item">
        <strong>{{ summary.total }}</strong><span>当前列表</span>
      </div>
    </div>

    <a-card class="page-card">
      <a-space wrap style="margin-bottom: 16px">
        <a-radio-group v-model:value="activeStatus" button-style="solid" @change="load">
          <a-radio-button value="">全部</a-radio-button>
          <a-radio-button value="SHIPPED">待反馈</a-radio-button>
          <a-radio-button value="FEEDBACK_PASSED">已通过</a-radio-button>
        </a-radio-group>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索产品、样品编号"
          style="width: 280px"
          allow-clear
          @search="load"
        />
      </a-space>

      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="router.push(`/shipment/${record.id}`)">
              {{ record.status === "SHIPPED" ? "录入反馈" : "查看详情" }}
            </a-button>
          </template>
        </template>
      </a-table>

      <a-empty v-if="!loading && rows.length === 0">
        <template #description>
          <span>{{ activeStatus === "SHIPPED" ? "暂无待反馈寄样" : "暂无寄样记录" }}</span>
        </template>
        <a-button v-if="canRecord" type="primary" @click="router.push('/shipment/record')">去登记寄样</a-button>
      </a-empty>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { canAccessRoute, type ShipmentRecord } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const keyword = ref("");
const activeStatus = ref("SHIPPED");
const rows = ref<ShipmentRecord[]>([]);
const canRecord = computed(() => canAccessRoute(auth.role, "/shipment/record", "pc"));

const summary = computed(() => ({
  total: rows.value.length,
  pending: rows.value.filter((item) => item.status === "SHIPPED").length,
  passed: rows.value.filter((item) => item.status === "FEEDBACK_PASSED").length,
}));

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo", width: 120 },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode", width: 80 },
  { title: "收件人", dataIndex: "receiverName", key: "receiverName", width: 100 },
  { title: "快递单号", dataIndex: "trackingNo", key: "trackingNo", width: 140 },
  { title: "状态", key: "status", width: 110 },
  { title: "操作", key: "action", width: 100 },
];

function statusLabel(status: string) {
  const map: Record<string, string> = {
    SHIPPED: "待反馈",
    FEEDBACK_PASSED: "客户通过",
    FEEDBACK_FAILED_RESAMPLE: "复打样",
    STOPPED: "已停止",
  };
  return map[status] ?? status;
}

function statusColor(status: string) {
  const map: Record<string, string> = {
    SHIPPED: "processing",
    FEEDBACK_PASSED: "success",
    FEEDBACK_FAILED_RESAMPLE: "warning",
    STOPPED: "error",
  };
  return map[status] ?? "default";
}

async function load() {
  loading.value = true;
  try {
    rows.value = await api.shipment.list({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
    });
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
