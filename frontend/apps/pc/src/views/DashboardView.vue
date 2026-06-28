<template>
  <div>
    <a-page-header title="工作台" sub-title="研发内勤工作台：录入样品需求、跟进寄样反馈和核价文件状态" />
    <a-spin :spinning="loading">
      <a-row :gutter="16" style="margin-bottom: 16px">
        <a-col v-for="link in primaryActions" :key="link.label" :span="8">
          <a-card hoverable @click="router.push(link.path)">
            <a-card-meta :title="link.label" :description="link.description" />
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col v-for="item in stats" :key="item.label" :span="6">
          <a-card class="stat-card" :bordered="false">
            <a-statistic :title="item.label" :value="item.value" :value-style="{ color: item.color }" />
          </a-card>
        </a-col>
      </a-row>

      <a-card title="最近待办任务" class="page-card" style="margin-top: 16px">
        <a-table :columns="taskColumns" :data-source="overview?.recentTasks ?? []" row-key="taskId" :pagination="false" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button size="small" @click="router.push(`/rnd/tasks/${record.taskId}`)">处理</a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card title="待核价文件" class="page-card">
        <a-table :columns="pricingColumns" :data-source="overview?.pendingPricingFiles ?? []" row-key="pricingFileId" :pagination="false" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button size="small" @click="router.push(`/pricing/${record.pricingFileId}`)">查看</a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-row :gutter="16">
        <a-col v-for="link in drilldowns" :key="link.label" :span="6">
          <a-card hoverable @click="router.push(link.path)">
            <a-card-meta :title="link.label" :description="link.description" />
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { DashboardOverview } from "@rnd/shared";
import { api } from "../services/api";

const router = useRouter();
const loading = ref(false);
const overview = ref<DashboardOverview | null>(null);

const stats = computed(() => {
  const data = overview.value;
  if (!data) return [];
  return [
    { label: "待总监审核", value: data.pendingReviewCount, color: "#f59e0b" },
    { label: "任务池待分发", value: data.pendingAssignmentCount, color: "#6d5bd0" },
    { label: "研发处理中", value: data.pendingAcceptanceCount + data.samplingCount, color: "#16a34a" },
    { label: "待财务核价", value: data.pendingPricingCount, color: "#ef4444" },
    { label: "样品已完成", value: data.completedSampleCount, color: "#16a34a" },
    { label: "已通知财务", value: data.financeNotifiedCount, color: "#246bfe" },
    { label: "停止/废弃", value: data.stoppedCount, color: "#ef4444" },
    { label: "待内部测试", value: data.pendingTestCount, color: "#0d9488" },
  ];
});

const primaryActions = [
  { label: "录入样品需求", description: "研发内勤录入业务员或客户提出的样品清单", path: "/demand/new" },
  { label: "寄样反馈", description: "查看待反馈样品，登记客户是否通过或继续打样", path: "/shipment/list" },
  { label: "核价文件", description: "样品通过后生成核价文件并通知财务", path: "/pricing/list" },
];

const drilldowns = [
  { label: "已提交需求", description: "查看需求审核进度", path: "/demand/review" },
  { label: "任务池进度", description: "查看需求通过后的研发任务状态", path: "/rnd/pool" },
  { label: "停止项目池", description: "查看停止或废弃的样品记录", path: "/rnd/stopped" },
  { label: "文件归档", description: "查看需求、实验、测试、寄样、核价归档", path: "/archive" },
];

const taskColumns = [
  { title: "任务", dataIndex: "taskId", key: "taskId" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "负责人", dataIndex: "assigneeName", key: "assigneeName" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

const pricingColumns = [
  { title: "文件", dataIndex: "pricingFileId", key: "pricingFileId" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

onMounted(async () => {
  loading.value = true;
  try {
    overview.value = await api.dashboard.overview();
  } finally {
    loading.value = false;
  }
});
</script>
