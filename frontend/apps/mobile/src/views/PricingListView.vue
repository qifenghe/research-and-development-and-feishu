<template>
  <div>
    <PageHeader :title="pageTitle" :subtitle="pageSubtitle" />

    <van-search
      v-model="keyword"
      shape="round"
      placeholder="搜索产品名称 / 样品编号"
      @search="load"
      @update:model-value="load"
    />

    <div class="chip-row">
      <button
        v-for="chip in chips"
        :key="chip.value"
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': activeStatus === chip.value }"
        @click="selectStatus(chip.value)"
      >
        {{ chip.label }}
      </button>
    </div>

    <van-empty v-if="loadError" image="error" :description="loadError">
      <van-button round type="primary" @click="load">重新加载</van-button>
    </van-empty>

    <van-list v-else v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load">
      <TaskCard
        v-for="file in rows"
        :key="file.id"
        :to="`/pricing/${file.id}`"
        :title="file.productName"
        :meta="pricingMeta(file)"
        action-label="查看"
        :status-symbol="pricingSymbol(file.status)"
      />
    </van-list>

    <van-empty v-if="!loading && !loadError && rows.length === 0" :description="emptyDescription" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { PricingFileRecord, PricingFileStatus } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import TaskCard from "../components/TaskCard.vue";
import { api } from "../services/api";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const keyword = ref("");
const activeStatus = ref<PricingFileStatus | "">("");
const loading = ref(false);
const finished = ref(true);
const rows = ref<PricingFileRecord[]>([]);
const loadError = ref("");
const pageTitle = computed(() => auth.role === "FINANCE" ? "财务收件箱" : "核价文件");
const pageSubtitle = computed(() => auth.role === "FINANCE" ? "查看、下载并确认接收核价文件" : "按审核状态处理核价文件");
const emptyDescription = computed(() => auth.role === "FINANCE" ? "暂无待接收核价文件" : "暂无核价文件");

const chips = computed<Array<{ label: string; value: PricingFileStatus | "" }>>(() => (
  auth.role === "FINANCE"
    ? [
        { label: "待接收", value: "FINANCE_NOTIFIED" },
        { label: "已接收", value: "FINANCE_RECEIVED" },
        { label: "全部", value: "" },
      ]
    : [
        { label: "待审核", value: "PENDING_PRICING_REVIEW" },
        { label: "待通知财务", value: "PRICING_APPROVED" },
        { label: "已退回", value: "PRICING_REJECTED" },
        { label: "已通知", value: "FINANCE_NOTIFIED" },
        { label: "已接收", value: "FINANCE_RECEIVED" },
        { label: "全部", value: "" },
      ]
));

function statusLabel(status: PricingFileStatus) {
  if (status === "FINANCE_RECEIVED") return "财务已接收";
  if (status === "FINANCE_NOTIFIED") return "待财务接收";
  if (status === "PRICING_APPROVED") return "待通知财务";
  if (status === "PRICING_REJECTED") return "核价已退回";
  return "待核价审核";
}

function pricingMeta(file: PricingFileRecord) {
  return [
    file.sampleNo,
    file.versionCode,
    file.pricingVersion,
    statusLabel(file.status),
  ].filter(Boolean).join(" · ");
}

function pricingSymbol(status: PricingFileStatus) {
  if (status === "FINANCE_RECEIVED") return "收";
  if (status === "PRICING_REJECTED") return "退";
  return "价";
}

function selectStatus(value: PricingFileStatus | "") {
  activeStatus.value = value;
  load();
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    rows.value = await api.shipment.pricingFiles({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
    }) as PricingFileRecord[];
  } catch (error) {
    rows.value = [];
    loadError.value = error instanceof Error ? error.message : "核价文件加载失败";
  } finally {
    loading.value = false;
    finished.value = true;
  }
}

onMounted(() => {
  activeStatus.value = auth.role === "FINANCE" ? "FINANCE_NOTIFIED" : "PENDING_PRICING_REVIEW";
  load();
});
</script>
