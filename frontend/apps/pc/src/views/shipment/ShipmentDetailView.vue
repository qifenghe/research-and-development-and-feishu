<template>
  <div>
    <a-page-header :title="detail?.shipment.productName ?? '寄样反馈详情'" sub-title="登记寄样信息、客户反馈，通过后生成核价文件" />
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
          <a-card title="客户反馈">
            <a-form layout="vertical" :model="feedback" @finish="submitFeedback">
              <a-form-item label="反馈结果" name="result" :rules="[{ required: true }]">
                <a-select v-model:value="feedback.result" :options="resultOptions" />
              </a-form-item>
              <a-form-item label="备注" name="comment"><a-textarea v-model:value="feedback.comment" /></a-form-item>
              <a-button type="primary" html-type="submit">提交客户反馈</a-button>
            </a-form>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card title="快捷操作">
            <a-space direction="vertical" style="width: 100%">
              <a-button type="primary" block @click="createPricing">客户通过，生成核价文件</a-button>
              <a-button block @click="router.push('/pricing/list')">查看核价文件列表</a-button>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { ShipmentDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const detail = ref<ShipmentDetailView | null>(null);
const feedback = reactive({ result: "PASSED", comment: "" });
const resultOptions = [
  { label: "客户通过", value: "PASSED" },
  { label: "客户不通过，继续打样", value: "FAILED_RESAMPLE" },
  { label: "停止打样", value: "STOPPED" },
];

async function load() {
  loading.value = true;
  try {
    detail.value = await api.shipment.shipmentDetail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
}

async function submitFeedback() {
  try {
    await api.shipment.submitFeedback(String(route.params.id), {
      ...feedback,
      feedbackBy: auth.displayName,
    });
    message.success("客户反馈已提交");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
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
