<template>
  <div class="page-with-footer">
    <van-skeleton title :row="6" :loading="loading">
      <PageHeader :title="headerTitle" compact>
        <StatusBadge :label="statusBadge.label" :variant="statusBadge.variant" />
      </PageHeader>

      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="load">重新加载</van-button>
      </van-empty>

      <InfoCard
        v-else
        v-for="group in resolvedGroups"
        :key="group.title"
        :title="group.title"
        :rows="group.fields"
      />
    </van-skeleton>

    <FixedActionBar :with-tabbar="false">
      <van-button type="primary" block :loading="downloading" @click="download">
        下载核价文件
      </van-button>
      <van-button
        v-if="canReview"
        type="primary"
        block
        :loading="reviewing"
        @click="review('APPROVE')"
      >
        审核通过
      </van-button>
      <van-field v-if="canReview" v-model="reviewComment" label="退回原因" placeholder="退回时必填" />
      <van-button
        v-if="canReview"
        type="danger"
        block
        :loading="reviewing"
        @click="review('REJECT')"
      >
        退回核价
      </van-button>
      <van-button
        v-if="canNotifyFinance"
        type="primary"
        block
        :loading="notifying"
        @click="notifyFinance"
      >
        通知财务
      </van-button>
      <van-button
        v-if="canReceive"
        type="success"
        block
        :loading="receiving"
        @click="receive"
      >
        确认接收
      </van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import { resolvePricingDetailFields, type PricingFileDetailView } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import InfoCard from "../components/InfoCard.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const downloading = ref(false);
const receiving = ref(false);
const notifying = ref(false);
const reviewing = ref(false);
const reviewComment = ref("");
const detail = ref<PricingFileDetailView | null>(null);
const loadError = ref("");
const canReceive = computed(() => (
  auth.role === "FINANCE" && detail.value?.pricingFile.status === "FINANCE_NOTIFIED"
));
const canNotifyFinance = computed(() => (
  auth.role === "RND_ASSISTANT" && detail.value?.pricingFile.status === "PRICING_APPROVED"
));
const canReview = computed(() => (
  (auth.role === "RND_DIRECTOR" || auth.role === "RND_ENGINEER")
  && detail.value?.pricingFile.status === "PENDING_PRICING_REVIEW"
));

const headerTitle = computed(() => {
  if (!detail.value) return "核价文件";
  return `${detail.value.pricingFile.productName} ${detail.value.pricingFile.versionCode}`;
});

const statusBadge = computed(() => {
  const status = detail.value?.pricingFile.status;
  if (status === "FINANCE_NOTIFIED") {
    return { label: "待财务接收", variant: "primary" as const };
  }
  if (status === "FINANCE_RECEIVED") {
    return { label: "财务已接收", variant: "success" as const };
  }
  if (status === "PRICING_APPROVED") return { label: "审核通过", variant: "success" as const };
  if (status === "PRICING_REJECTED") return { label: "核价退回", variant: "danger" as const };
  return { label: "待核价审核", variant: "primary" as const };
});

const resolvedGroups = computed(() => {
  if (!detail.value) return [];
  return resolvePricingDetailFields(detail.value).map((group) => ({
    title: group.title,
    fields: group.fields.map((field) => ({
      label: field.label,
      value: String(field.value ?? "-"),
    })),
  }));
});

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    detail.value = await api.shipment.pricingDetail(String(route.params.id), auth.role);
  } catch (error) {
    detail.value = null;
    loadError.value = error instanceof Error ? error.message : "核价文件加载失败";
  } finally {
    loading.value = false;
  }
}

async function receive() {
  receiving.value = true;
  try {
    await api.shipment.receivePricingFile(String(route.params.id), auth.displayName);
    showSuccessToast("已确认接收");
    await router.replace("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "确认接收失败");
  } finally {
    receiving.value = false;
  }
}

async function notifyFinance() {
  notifying.value = true;
  try {
    await api.shipment.notifyFinance(String(route.params.id), "财务");
    showSuccessToast("已通知财务");
    await router.replace("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "通知财务失败");
  } finally {
    notifying.value = false;
  }
}

async function review(decision: "APPROVE" | "REJECT") {
  reviewing.value = true;
  try {
    await api.shipment.reviewPricingFile(String(route.params.id), {
      decision,
      reviewerName: auth.displayName,
      comment: reviewComment.value.trim() || undefined,
    });
    showSuccessToast(decision === "APPROVE" ? "核价已审核通过" : "核价已退回");
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "核价审核失败");
  } finally {
    reviewing.value = false;
  }
}

async function download() {
  downloading.value = true;
  try {
    const blob = await api.shipment.downloadPricingFile(String(route.params.id));
    downloadBlob(blob, detail.value?.pricingFile.fileName ?? "pricing.xlsx");
    showSuccessToast("已开始下载");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "下载失败");
  } finally {
    downloading.value = false;
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

onMounted(load);
</script>
