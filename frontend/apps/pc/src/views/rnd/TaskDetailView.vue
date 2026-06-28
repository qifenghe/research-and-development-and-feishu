<template>
  <div>
    <a-page-header :title="detail?.task.productName ?? '研发任务详情'" sub-title="按角色和状态展示字段分组、实验单和可点击操作按钮" />
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
        </a-col>
        <a-col :span="8">
          <a-card title="可执行操作">
            <a-space direction="vertical" style="width: 100%">
              <a-button
                v-for="action in detail.availableActions"
                :key="action.code"
                :type="action.primary ? 'primary' : 'default'"
                block
                @click="runAction(action.code)"
              >
                {{ action.label }}
              </a-button>
              <a-button
                v-if="detail.task.status === 'COMPLETED'"
                type="primary"
                block
                @click="shipmentModalOpen = true"
              >
                登记寄样
              </a-button>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
    <a-modal
      v-model:open="shipmentModalOpen"
      title="登记寄样"
      ok-text="确认寄样"
      :confirm-loading="shipmentSubmitting"
      @ok="createShipment"
    >
      <a-form layout="vertical">
        <a-form-item label="寄样数量">
          <a-input-number v-model:value="shipment.quantity" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="收件人">
          <a-input v-model:value="shipment.receiverName" />
        </a-form-item>
        <a-form-item label="快递单号">
          <a-input v-model:value="shipment.trackingNo" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="shipment.remark" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { RndTaskDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const detail = ref<RndTaskDetailView | null>(null);
const shipmentModalOpen = ref(false);
const shipmentSubmitting = ref(false);
const shipment = reactive({
  quantity: 6,
  receiverName: "销售内勤",
  trackingNo: "",
  remark: "寄客户品尝确认",
});

async function load() {
  loading.value = true;
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
}

async function runAction(code: string) {
  const taskId = String(route.params.id);
  try {
    if (code === "ACCEPT_TASK") {
      await api.task.accept(taskId, auth.displayName);
      message.success("已接受任务");
    } else if (code === "OPEN_EXPERIMENT") {
      router.push({
        path: `/rnd/history/${detail.value?.version.id}`,
        query: {
          projectId: detail.value?.version.projectId,
          productName: detail.value?.task.productName,
          taskId,
        },
      });
      return;
    }
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "操作失败");
  }
}

async function createShipment() {
  if (!detail.value || !shipment.receiverName.trim() || !shipment.trackingNo.trim()) {
    message.warning("请填写收件人和快递单号");
    return;
  }
  shipmentSubmitting.value = true;
  try {
    const record = await api.shipment.createShipment(detail.value.version.id, { ...shipment });
    shipmentModalOpen.value = false;
    message.success("寄样记录已生成");
    router.push(`/shipment/${record.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "寄样登记失败");
  } finally {
    shipmentSubmitting.value = false;
  }
}

onMounted(load);
</script>
