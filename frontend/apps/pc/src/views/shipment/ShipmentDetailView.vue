<template>
  <div>
    <a-page-header
      :title="detail?.shipment.productName ?? '寄样反馈详情'"
      sub-title="登记寄样信息、客户反馈，通过后生成核价文件"
      @back="router.back()"
    >
      <template v-if="detail" #tags>
        <a-tag :color="statusColor(detail.shipment.status)">{{ statusLabel(detail.shipment.status) }}</a-tag>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <a-row v-if="detail" :gutter="16">
        <a-col :span="16">
          <a-card v-for="group in resolvedGroups" :key="group.title" :title="group.title" class="page-card">
            <a-descriptions bordered size="small" :column="2">
              <a-descriptions-item v-for="field in group.fields" :key="field.label" :label="field.label">
                {{ field.value }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card v-if="showFeedbackForm" title="客户反馈" class="page-card">
            <p class="page-card__hint">请选择客户试吃结论，并补充备注说明。</p>
            <div class="feedback-result-group">
              <button
                v-for="option in resultOptions"
                :key="option.value"
                type="button"
                class="feedback-result-option"
                :class="[
                  `feedback-result-option--${option.tone}`,
                  { 'feedback-result-option--active': feedback.result === option.value },
                ]"
                @click="feedback.result = option.value"
              >
                {{ option.label }}
              </button>
            </div>
            <a-form layout="vertical" :model="feedback" @finish="submitFeedback">
              <a-form-item label="反馈备注" name="comment">
                <a-textarea v-model:value="feedback.comment" :rows="3" placeholder="客户试吃后反馈…" />
              </a-form-item>
              <a-space>
                <a-button type="primary" html-type="submit" :loading="submitting">
                  {{ submitButtonLabel }}
                </a-button>
                <a-button
                  v-if="feedback.result === 'PASSED'"
                  :loading="pricingSubmitting"
                  @click="submitAndCreatePricing"
                >
                  提交并通过，生成核价
                </a-button>
              </a-space>
            </a-form>
          </a-card>

          <a-alert
            v-else-if="detail.shipment.status === 'SHIPPED' && !canRecordFeedback"
            type="warning"
            show-icon
            message="当前角色无权录入客户反馈"
          />
          <a-alert
            v-else-if="detail.shipment.status !== 'SHIPPED'"
            type="success"
            show-icon
            message="该寄样已处理，可在右侧继续后续操作"
          />
        </a-col>

        <a-col :span="8">
          <a-card title="后续操作" class="page-card workflow-side-card">
            <a-typography-text type="secondary">
              客户通过后生成核价文件，产品负责人或研发总监审核通过后自动移交财务。
            </a-typography-text>
            <a-space direction="vertical" style="width: 100%; margin-top: 16px">
              <a-button
                type="primary"
                block
                :disabled="detail.shipment.status !== 'FEEDBACK_PASSED'"
                @click="createPricing"
              >
                生成核价文件
              </a-button>
              <a-button block @click="router.push('/pricing/list')">查看核价文件列表</a-button>
              <a-button block @click="router.push('/shipment/list')">返回寄样列表</a-button>
            </a-space>
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
import { canPerformAction, resolveShipmentDetailFields, type ShipmentDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const pricingSubmitting = ref(false);
const detail = ref<ShipmentDetailView | null>(null);
const feedback = reactive({ result: "PASSED" as "PASSED" | "FAILED_RESAMPLE" | "STOPPED", comment: "" });
const canRecordFeedback = computed(() => canPerformAction(auth.role, "RECORD_SHIPMENT_FEEDBACK"));
const showFeedbackForm = computed(() => detail.value?.shipment.status === "SHIPPED" && canRecordFeedback.value);
const resolvedGroups = computed(() => (detail.value ? resolveShipmentDetailFields(detail.value) : []));
const resultOptions = [
  { label: "客户通过", value: "PASSED" as const, tone: "pass" },
  { label: "继续打样", value: "FAILED_RESAMPLE" as const, tone: "resample" },
  { label: "停止打样", value: "STOPPED" as const, tone: "stop" },
];

const submitButtonLabel = computed(() => {
  if (feedback.result === "PASSED") return "提交客户通过";
  if (feedback.result === "FAILED_RESAMPLE") return "提交并继续打样";
  return "提交并停止打样";
});

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
    detail.value = await api.shipment.shipmentDetail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
}

async function submitFeedback() {
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(String(route.params.id), {
      ...feedback,
      feedbackBy: auth.displayName,
    });
    message.success("客户反馈已提交");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function submitAndCreatePricing() {
  pricingSubmitting.value = true;
  try {
    await api.shipment.submitFeedback(String(route.params.id), {
      result: "PASSED",
      comment: feedback.comment,
      feedbackBy: auth.displayName,
    });
    if (detail.value) {
      const file = await api.shipment.createPricingFile(detail.value.version.id);
      message.success("核价文件已生成");
      router.push(`/pricing/${file.id}`);
      return;
    }
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "操作失败");
  } finally {
    pricingSubmitting.value = false;
  }
}

async function createPricing() {
  if (!detail.value) return;
  try {
    const file = await api.shipment.createPricingFile(detail.value.version.id);
    message.success("核价文件已生成");
    router.push(`/pricing/${file.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "生成失败");
  }
}

onMounted(load);
</script>

<style scoped>
.page-card__hint {
  color: #64748b;
  margin: 0 0 12px;
}
</style>
