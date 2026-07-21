<template>
  <div>
    <a-page-header
      title="报表导出"
      sub-title="导出实验单、测试单、核价文件、打样任务列表和寄样反馈列表"
    />

    <a-row :gutter="[16, 16]">
      <a-col :span="12">
        <a-card title="打样任务列表">
          <a-form layout="vertical">
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="名称/编号/负责人">
                  <a-input v-model:value="taskFilter.keyword" allow-clear placeholder="输入产品名称、样品编号或负责人" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="状态">
                  <a-select v-model:value="taskFilter.status" allow-clear placeholder="全部状态">
                    <a-select-option value="PENDING_ASSIGNMENT">待分发</a-select-option>
                    <a-select-option value="PENDING_ACCEPTANCE">待接受</a-select-option>
                    <a-select-option value="SAMPLING">打样中</a-select-option>
                    <a-select-option value="PENDING_TEST">待测试</a-select-option>
                    <a-select-option value="COMPLETED">已完成</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="开始日期">
                  <a-input v-model:value="taskFilter.startDate" type="date" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="结束日期">
                  <a-input v-model:value="taskFilter.endDate" type="date" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-button type="primary" :loading="loading === 'tasks'" @click="exportTasks">导出打样列表 Excel</a-button>
          </a-form>
        </a-card>
      </a-col>

      <a-col :span="12">
        <a-card title="寄样反馈列表">
          <a-form layout="vertical">
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="产品/客户/快递单">
                  <a-input v-model:value="shipmentFilter.keyword" allow-clear placeholder="输入产品名称或快递单号" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="状态">
                  <a-select v-model:value="shipmentFilter.status" allow-clear placeholder="全部状态">
                    <a-select-option value="PENDING_SHIPMENT">待寄样</a-select-option>
                    <a-select-option value="SHIPPED">已寄样待反馈</a-select-option>
                    <a-select-option value="FEEDBACK_PASSED">客户通过</a-select-option>
                    <a-select-option value="FEEDBACK_FAILED">客户不通过</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="开始日期">
                  <a-input v-model:value="shipmentFilter.startDate" type="date" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="结束日期">
                  <a-input v-model:value="shipmentFilter.endDate" type="date" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-button type="primary" :loading="loading === 'shipments'" @click="exportShipments">导出寄样反馈 Excel</a-button>
          </a-form>
        </a-card>
      </a-col>

      <a-col :span="24">
        <a-card title="按单据导出">
          <a-row :gutter="[16, 16]">
            <a-col :span="8">
              <a-input-search
                v-model:value="ids.experimentFormId"
                enter-button="导出实验单"
                placeholder="实验单 ID"
                :loading="loading === 'experiment'"
                @search="exportExperiment"
              />
            </a-col>
            <a-col :span="8">
              <a-input-search
                v-model:value="ids.testRecordId"
                enter-button="导出测试单"
                placeholder="测试记录 ID"
                :loading="loading === 'test'"
                @search="exportTest"
              />
            </a-col>
            <a-col :span="8">
              <a-input-search
                v-model:value="ids.pricingFileId"
                enter-button="导出核价文件"
                placeholder="核价文件 ID"
                :loading="loading === 'pricing'"
                @search="exportPricing"
              />
            </a-col>
          </a-row>
          <a-alert
            style="margin-top: 16px"
            type="info"
            show-icon
            message="说明"
            description="列表导出适合批量汇总；按单据导出适合从详情页复制 ID 后补导。后续可在详情页继续增加一键导出按钮。"
          />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { api } from "../../services/api";

type LoadingKey = "" | "tasks" | "shipments" | "experiment" | "test" | "pricing";

const loading = ref<LoadingKey>("");
const taskFilter = reactive({
  keyword: "",
  status: undefined as string | undefined,
  startDate: "",
  endDate: "",
});
const shipmentFilter = reactive({
  keyword: "",
  status: undefined as string | undefined,
  startDate: "",
  endDate: "",
});
const ids = reactive({
  experimentFormId: "",
  testRecordId: "",
  pricingFileId: "",
});

async function exportTasks() {
  await exportFile("tasks", () => api.report.exportRndTasks(taskFilter), `打样任务列表-${dateText()}.xlsx`);
}

async function exportShipments() {
  await exportFile("shipments", () => api.report.exportShipments(shipmentFilter), `寄样反馈列表-${dateText()}.xlsx`);
}

async function exportExperiment() {
  if (!ids.experimentFormId.trim()) return message.warning("请先输入实验单 ID");
  await exportFile("experiment", () => api.report.exportExperimentForm(ids.experimentFormId.trim()), `打样实验单-${dateText()}.xlsx`);
}

async function exportTest() {
  if (!ids.testRecordId.trim()) return message.warning("请先输入测试记录 ID");
  await exportFile("test", () => api.report.exportTestRecord(ids.testRecordId.trim()), `内部测试单-${dateText()}.xlsx`);
}

async function exportPricing() {
  if (!ids.pricingFileId.trim()) return message.warning("请先输入核价文件 ID");
  await exportFile("pricing", () => api.report.exportPricingFile(ids.pricingFileId.trim()), `核价文件-${dateText()}.xlsx`);
}

async function exportFile(key: LoadingKey, loader: () => Promise<Blob>, fallbackName: string) {
  loading.value = key;
  try {
    const blob = await loader();
    download(blob, fallbackName);
    message.success("导出成功");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "导出失败");
  } finally {
    loading.value = "";
  }
}

function download(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function dateText() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}${month}${day}`;
}
</script>
