<template>
  <div class="page-with-footer">
    <van-skeleton title :row="5" :loading="loading">
      <PageHeader :title="headerTitle" compact>
        <StatusBadge :label="statusBadge.label" :variant="statusBadge.variant" />
      </PageHeader>

      <InfoCard title="寄样信息" :rows="shipmentFields" />

      <div class="form-panel">
        <van-field
          v-model="feedback.comment"
          rows="3"
          autosize
          type="textarea"
          label="反馈备注"
          placeholder="客户试吃后反馈…"
        />
      </div>

      <div class="section-title">处理结论</div>
      <ConclusionButtons v-model="feedback.result" :options="conclusionOptions" />
    </van-skeleton>

    <FixedActionBar :with-tabbar="false">
      <van-button
        type="primary"
        block
        :loading="submitting"
        @click="feedback.result === 'PASSED' ? submitAndCreatePricing() : submitFeedback()"
      >
        {{ primaryButtonLabel }}
      </van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { ShipmentDetailView } from "@rnd/shared";
import { resolveShipmentDetailFields } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import InfoCard from "../components/InfoCard.vue";
import ConclusionButtons, { type ConclusionOption } from "../components/ConclusionButtons.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const detail = ref<ShipmentDetailView | null>(null);
const feedback = reactive({
  result: "PASSED" as "PASSED" | "FAILED_RESAMPLE" | "STOPPED",
  comment: "",
});

const conclusionOptions: ConclusionOption[] = [
  { value: "PASSED", label: "客户通过", variant: "pass" },
  { value: "FAILED_RESAMPLE", label: "继续打样", variant: "resample" },
  { value: "STOPPED", label: "停止打样", variant: "stop" },
];

const headerTitle = computed(() => {
  if (!detail.value) return "寄样反馈";
  return `${detail.value.shipment.productName} ${detail.value.shipment.versionCode}`;
});

const statusBadge = computed(() => {
  const status = detail.value?.shipment.status;
  if (status === "FEEDBACK_PASSED") {
    return { label: "客户已通过", variant: "success" as const };
  }
  if (status === "SHIPPED") {
    return { label: "已寄样待反馈", variant: "primary" as const };
  }
  return { label: "寄样反馈", variant: "warning" as const };
});

const primaryButtonLabel = computed(() => {
  if (feedback.result === "PASSED") return "客户通过，生成核价文件";
  if (feedback.result === "FAILED_RESAMPLE") return "提交并继续打样";
  return "提交并停止打样";
});

const shipmentFields = computed(() => {
  if (!detail.value) return [];
  return resolveShipmentDetailFields(detail.value)
    .flatMap((group) => group.fields)
    .filter((field) => field.label !== "寄样记录编号");
});

onMounted(async () => {
  loading.value = true;
  try {
    detail.value = await api.shipment.shipmentDetail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
});

async function submitFeedback() {
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(String(route.params.id), {
      result: feedback.result,
      comment: feedback.comment,
      feedbackBy: auth.displayName,
    });
    showSuccessToast("反馈已提交");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function submitAndCreatePricing() {
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(String(route.params.id), {
      result: "PASSED",
      comment: feedback.comment,
      feedbackBy: auth.displayName,
    });
    if (detail.value) {
      await api.shipment.createPricingFile(detail.value.version.id);
    }
    showSuccessToast("核价文件已生成");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}
</script>
