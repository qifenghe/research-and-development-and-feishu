<template>
  <div>
    <a-page-header
      title="录入客户反馈"
      sub-title="研发内勤登记寄样信息，并录入客户试吃后的通过、复打样或停止结论"
    >
      <template #extra>
        <a-space>
          <a-button :loading="seeding" @click="seedDemo">加载演示数据</a-button>
          <a-button type="primary" @click="load">刷新列表</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <a-row :gutter="16" style="margin-bottom: 16px">
        <a-col :span="12">
          <a-card><a-statistic title="待录入反馈" :value="pendingFeedback.length" /></a-card>
        </a-col>
        <a-col :span="12">
          <a-card><a-statistic title="待登记寄样" :value="pendingShipment.length" /></a-card>
        </a-col>
      </a-row>

      <a-card title="待录入反馈" class="page-card">
        <template #extra><a-tag color="processing">已寄出 · 等待客户意见</a-tag></template>
        <a-table :columns="feedbackColumns" :data-source="pendingFeedback" row-key="id" :pagination="false" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="primary" size="small" @click="openFeedbackDrawer(record)">录入反馈</a-button>
            </template>
          </template>
        </a-table>
        <a-empty v-if="!loading && pendingFeedback.length === 0" description="暂无待反馈寄样" />
      </a-card>

      <a-card title="待登记寄样" class="page-card">
        <template #extra><a-tag color="warning">内部测试已通过 · 尚未寄出</a-tag></template>
        <a-table :columns="shipmentColumns" :data-source="pendingShipment" row-key="id" :pagination="false" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="primary" size="small" @click="openShipmentDrawer(record)">登记寄样</a-button>
            </template>
          </template>
        </a-table>
        <a-empty v-if="!loading && pendingShipment.length === 0" description="暂无待寄样样品" />
      </a-card>
    </a-spin>

    <a-drawer
      v-model:open="shipmentDrawerOpen"
      title="登记寄样"
      :width="520"
      destroy-on-close
    >
      <template v-if="activeTask">
        <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px">
          <a-descriptions-item label="产品">{{ activeTask.productName }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ activeTask.versionCode }}</a-descriptions-item>
          <a-descriptions-item label="样品编号">{{ activeTask.sampleNo }}</a-descriptions-item>
        </a-descriptions>
        <a-form layout="vertical" @finish="submitShipment">
          <a-form-item label="寄样数量" required>
            <a-input-number v-model:value="shipmentForm.quantity" :min="1" style="width: 100%" />
          </a-form-item>
          <a-form-item label="收件人" required>
            <a-input v-model:value="shipmentForm.receiverName" />
          </a-form-item>
          <a-form-item label="快递单号" required>
            <a-input v-model:value="shipmentForm.trackingNo" />
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="shipmentForm.remark" />
          </a-form-item>
          <a-button type="primary" html-type="submit" block :loading="submitting">提交登记</a-button>
        </a-form>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="feedbackDrawerOpen"
      title="录入客户反馈"
      :width="520"
      destroy-on-close
    >
      <template v-if="activeShipment">
        <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px">
          <a-descriptions-item label="产品">{{ activeShipment.productName }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ activeShipment.versionCode }}</a-descriptions-item>
          <a-descriptions-item label="快递单号">{{ activeShipment.trackingNo }}</a-descriptions-item>
        </a-descriptions>
        <a-form layout="vertical" @finish="submitFeedback">
          <a-form-item label="反馈结论" required>
            <a-radio-group v-model:value="feedbackForm.result">
              <a-radio value="PASSED">客户通过</a-radio>
              <a-radio value="FAILED_RESAMPLE">客户不通过，继续打样</a-radio>
              <a-radio value="STOPPED">停止打样</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="反馈备注">
            <a-textarea v-model:value="feedbackForm.comment" :rows="4" placeholder="客户试吃后的具体意见…" />
          </a-form-item>
          <a-space direction="vertical" style="width: 100%">
            <a-button type="primary" html-type="submit" block :loading="submitting">提交客户反馈</a-button>
            <a-button
              v-if="feedbackForm.result === 'PASSED'"
              block
              :loading="submitting"
              @click="submitFeedbackAndPricing"
            >
              客户通过并生成核价文件
            </a-button>
          </a-space>
        </a-form>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { RndTask, ShipmentRecord } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api, client } from "../../services/api";

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const seeding = ref(false);
const submitting = ref(false);
const pendingFeedback = ref<ShipmentRecord[]>([]);
const pendingShipment = ref<RndTask[]>([]);

const shipmentDrawerOpen = ref(false);
const feedbackDrawerOpen = ref(false);
const activeTask = ref<RndTask | null>(null);
const activeShipment = ref<ShipmentRecord | null>(null);

const shipmentForm = reactive({
  quantity: 6,
  receiverName: "销售内勤",
  trackingNo: "",
  remark: "寄客户试吃确认",
});

const feedbackForm = reactive({
  result: "PASSED" as "PASSED" | "FAILED_RESAMPLE" | "STOPPED",
  comment: "",
});

const feedbackColumns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "收件人", dataIndex: "receiverName", key: "receiverName" },
  { title: "快递单号", dataIndex: "trackingNo", key: "trackingNo" },
  { title: "操作", key: "action", width: 120 },
];

const shipmentColumns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "研发负责人", dataIndex: "assigneeName", key: "assigneeName" },
  { title: "操作", key: "action", width: 120 },
];

function openShipmentDrawer(task: RndTask) {
  activeTask.value = task;
  shipmentForm.trackingNo = `SF${Date.now().toString().slice(-10)}`;
  shipmentDrawerOpen.value = true;
}

function openFeedbackDrawer(shipment: ShipmentRecord) {
  activeShipment.value = shipment;
  feedbackForm.result = "PASSED";
  feedbackForm.comment = "";
  feedbackDrawerOpen.value = true;
}

async function load() {
  loading.value = true;
  try {
    const [shipped, completedTasks] = await Promise.all([
      api.shipment.list({ status: "SHIPPED" }) as Promise<ShipmentRecord[]>,
      api.task.list({ status: "COMPLETED" }) as Promise<RndTask[]>,
    ]);
    pendingFeedback.value = shipped;
    const allShipments = (await api.shipment.list()) as ShipmentRecord[];
    const shippedVersionIds = new Set(allShipments.map((item) => item.versionId));
    pendingShipment.value = completedTasks.filter((task) => !shippedVersionIds.has(task.versionId));
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

async function seedDemo() {
  seeding.value = true;
  try {
    await client.post("/demo/seed-feedback");
    message.success("演示数据已就绪");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载演示数据失败");
  } finally {
    seeding.value = false;
  }
}

async function submitShipment() {
  if (!activeTask.value || !shipmentForm.receiverName.trim() || !shipmentForm.trackingNo.trim()) {
    message.warning("请填写收件人和快递单号");
    return;
  }
  submitting.value = true;
  try {
    const created = await api.shipment.createShipment(activeTask.value.versionId, { ...shipmentForm });
    message.success("寄样已登记，可继续录入客户反馈");
    shipmentDrawerOpen.value = false;
    await load();
    openFeedbackDrawer(created);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "登记失败");
  } finally {
    submitting.value = false;
  }
}

async function submitFeedback() {
  if (!activeShipment.value) return;
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(activeShipment.value.id, {
      result: feedbackForm.result,
      comment: feedbackForm.comment,
      feedbackBy: auth.displayName,
    });
    message.success("客户反馈已提交");
    feedbackDrawerOpen.value = false;
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function submitFeedbackAndPricing() {
  if (!activeShipment.value) return;
  submitting.value = true;
  try {
    await api.shipment.submitFeedback(activeShipment.value.id, {
      result: "PASSED",
      comment: feedbackForm.comment,
      feedbackBy: auth.displayName,
    });
    const file = await api.shipment.createPricingFile(activeShipment.value.versionId);
    message.success(`已生成核价文件 ${file.pricingVersion}`);
    feedbackDrawerOpen.value = false;
    router.push(`/pricing/${file.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>
