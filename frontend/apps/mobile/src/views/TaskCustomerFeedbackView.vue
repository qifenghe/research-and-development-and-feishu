<template>
  <div>
    <van-skeleton title :row="6" :loading="loading">
      <PageHeader :title="headerTitle" compact />

      <template v-if="!shipment && taskDetail?.task.status === 'COMPLETED'">
        <van-notice-bar wrapable :scrollable="false" text="填写客户反馈前需先登记寄样快递信息" />
        <van-cell-group inset title="登记寄样">
          <van-field v-model.number="shipmentForm.quantity" type="digit" label="寄样数量" />
          <van-field v-model="shipmentForm.receiverName" label="收件人" required />
          <van-field v-model="shipmentForm.trackingNo" label="快递单号" required />
          <van-field v-model="shipmentForm.remark" label="备注" type="textarea" rows="2" autosize />
        </van-cell-group>
        <div style="padding: 16px">
          <van-button type="primary" block :loading="creatingShipment" @click="createShipment">
            登记寄样并继续
          </van-button>
        </div>
      </template>

      <template v-else-if="shipment">
        <InfoCard title="寄样信息" :rows="shipmentRows" />
        <van-field v-model="feedback.comment" rows="3" autosize type="textarea" label="反馈备注" placeholder="客户试吃后反馈…" />
        <div class="section-title">处理结论</div>
        <ConclusionButtons v-model="feedback.result" :options="conclusionOptions" />
      </template>
    </van-skeleton>

    <FixedActionBar v-if="shipment" :with-tabbar="false">
      <van-button
        v-if="feedback.result === 'PASSED'"
        type="primary"
        block
        :loading="submitting"
        @click="submitAndCreatePricing"
      >
        客户通过，生成核价文件
      </van-button>
      <van-button v-else type="primary" block :loading="submitting" @click="submitFeedback">提交客户反馈</van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { RndTaskDetailView, ShipmentRecord } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
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
const creatingShipment = ref(false);
const taskDetail = ref<RndTaskDetailView | null>(null);
const shipment = ref<ShipmentRecord | null>(null);
const feedback = reactive({
  result: "PASSED" as "PASSED" | "FAILED_RESAMPLE" | "STOPPED",
  comment: "",
});
const shipmentForm = reactive({
  quantity: 6,
  receiverName: "销售内勤",
  trackingNo: "",
  remark: "寄客户品尝确认",
});

const conclusionOptions: ConclusionOption[] = [
  { value: "PASSED", label: "客户通过", variant: "pass" },
  { value: "FAILED_RESAMPLE", label: "继续打样", variant: "resample" },
  { value: "STOPPED", label: "停止打样", variant: "stop" },
];

const headerTitle = computed(() => {
  if (taskDetail.value) return `${taskDetail.value.task.productName} 客户反馈`;
  return "录入客户反馈";
});

const shipmentRows = computed(() => {
  if (!shipment.value) return [];
  return [
    { label: "产品", value: shipment.value.productName },
    { label: "版本", value: shipment.value.versionCode },
    { label: "数量", value: String(shipment.value.quantity) },
    { label: "收件人", value: shipment.value.receiverName },
    { label: "快递单号", value: shipment.value.trackingNo },
  ];
});

async function loadShipment() {
  if (!taskDetail.value) return;
  const list = (await api.shipment.list({ keyword: taskDetail.value.task.sampleNo })) as ShipmentRecord[];
  shipment.value = list.find((item) => item.versionId === taskDetail.value!.version.id) ?? list[0] ?? null;
}

async function createShipment() {
  if (!taskDetail.value || !shipmentForm.receiverName.trim() || !shipmentForm.trackingNo.trim()) {
    showFailToast("请填写收件人和快递单号");
    return;
  }
  creatingShipment.value = true;
  try {
    shipment.value = await api.shipment.createShipment(taskDetail.value.version.id, { ...shipmentForm });
    showSuccessToast("寄样已登记");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "寄样登记失败");
  } finally {
    creatingShipment.value = false;
  }
}

async function submitFeedback() {
  if (!shipment.value) return;
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(shipment.value.id, {
      result: feedback.result,
      comment: feedback.comment,
      feedbackBy: auth.displayName,
    });
    showSuccessToast("客户反馈已提交");
    router.push("/shipments/record");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function submitAndCreatePricing() {
  if (!shipment.value || !taskDetail.value) return;
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(shipment.value.id, {
      result: "PASSED",
      comment: feedback.comment,
      feedbackBy: auth.displayName,
    });
    const file = await api.shipment.createPricingFile(taskDetail.value.version.id);
    showSuccessToast(`已生成核价文件 ${file.pricingVersion}`);
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    taskDetail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
    await loadShipment();
  } finally {
    loading.value = false;
  }
});
</script>
