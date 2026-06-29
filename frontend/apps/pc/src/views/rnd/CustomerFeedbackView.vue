<template>
  <div>
    <a-page-header
      :title="headerTitle"
      sub-title="外部反馈意见：客户或业务试吃后的通过、复打样或停止结论"
      @back="router.back()"
    />

    <a-spin :spinning="loading">
      <a-row :gutter="16">
        <a-col :span="16">
          <a-card v-if="!shipment && taskDetail?.task.status === 'COMPLETED'" title="先登记寄样" class="page-card">
            <a-alert type="info" show-icon message="填写外部反馈前需先登记寄样快递信息" style="margin-bottom: 16px" />
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :span="8">
                  <a-form-item label="寄样数量"><a-input-number v-model:value="shipmentForm.quantity" :min="1" style="width: 100%" /></a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="收件人" required><a-input v-model:value="shipmentForm.receiverName" /></a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="快递单号" required><a-input v-model:value="shipmentForm.trackingNo" /></a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="备注"><a-textarea v-model:value="shipmentForm.remark" /></a-form-item>
              <a-button type="primary" :loading="creatingShipment" @click="createShipment">登记寄样并继续填写反馈</a-button>
            </a-form>
          </a-card>

          <template v-else-if="shipment">
            <a-card title="寄样信息" class="page-card">
              <a-descriptions bordered size="small" :column="2">
                <a-descriptions-item label="产品">{{ shipment.productName }}</a-descriptions-item>
                <a-descriptions-item label="版本">{{ shipment.versionCode }}</a-descriptions-item>
                <a-descriptions-item label="数量">{{ shipment.quantity }}</a-descriptions-item>
                <a-descriptions-item label="收件人">{{ shipment.receiverName }}</a-descriptions-item>
                <a-descriptions-item label="快递单号" :span="2">{{ shipment.trackingNo }}</a-descriptions-item>
              </a-descriptions>
            </a-card>

            <a-card title="外部反馈意见" class="page-card">
              <a-form layout="vertical">
                <a-form-item label="反馈备注">
                  <a-textarea v-model:value="feedback.comment" :rows="4" placeholder="客户试吃后的具体意见…" />
                </a-form-item>
                <a-form-item label="处理结论" required>
                  <a-radio-group v-model:value="feedback.result">
                    <a-radio value="PASSED">客户通过</a-radio>
                    <a-radio value="FAILED_RESAMPLE">客户不通过，继续打样</a-radio>
                    <a-radio value="STOPPED">停止打样</a-radio>
                  </a-radio-group>
                </a-form-item>
              </a-form>
            </a-card>
          </template>
        </a-col>

        <a-col :span="8">
          <a-card title="提交操作" class="page-card">
            <a-button
              v-if="shipment?.status === 'SHIPPED' && feedback.result === 'PASSED'"
              type="primary"
              block
              :loading="submitting"
              @click="submitAndCreatePricing"
            >
              客户通过，生成核价文件
            </a-button>
            <a-button
              v-else-if="shipment"
              type="primary"
              block
              :loading="submitting"
              :disabled="shipment.status !== 'SHIPPED'"
              @click="submitFeedback"
            >
              提交外部反馈意见
            </a-button>
            <a-typography-text v-else type="secondary">请先完成寄样登记</a-typography-text>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { RndTaskDetailView, ShipmentRecord } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

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

const headerTitle = computed(() => {
  if (taskDetail.value) return `${taskDetail.value.task.productName} 外部反馈`;
  return "外部反馈意见";
});

async function loadShipment() {
  if (!taskDetail.value) return;
  const list = await api.shipment.list({ keyword: taskDetail.value.task.sampleNo });
  shipment.value =
    list.find((item) => item.versionId === taskDetail.value!.version.id) ?? list[0] ?? null;
}

async function createShipment() {
  if (!taskDetail.value || !shipmentForm.receiverName.trim() || !shipmentForm.trackingNo.trim()) {
    message.warning("请填写收件人和快递单号");
    return;
  }
  creatingShipment.value = true;
  try {
    shipment.value = await api.shipment.createShipment(taskDetail.value.version.id, { ...shipmentForm });
    message.success("寄样已登记，可填写外部反馈");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "寄样登记失败");
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
    message.success("外部反馈已提交");
    router.push(`/rnd/tasks/${route.params.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
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
    message.success(`已生成核价文件 ${file.pricingVersion}`);
    router.push(`/pricing/${file.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "操作失败");
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
