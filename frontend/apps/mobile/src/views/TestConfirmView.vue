<template>
  <div class="page-with-footer">
    <van-skeleton title :row="5" :loading="loading">
      <PageHeader :title="headerTitle" compact>
        <StatusBadge label="待测试" variant="primary" />
      </PageHeader>

      <van-empty v-if="loadError" class="mobile-empty" image="error" :description="loadError">
        <van-button type="primary" @click="load">重新加载</van-button>
      </van-empty>

      <template v-else>
      <InfoCard title="实验单摘要">
        <div class="info-row">
          <span class="info-row__value" style="max-width: 100%; text-align: left; white-space: pre-wrap">
            {{ experimentSummary }}
          </span>
        </div>
      </InfoCard>

      <div class="form-panel">
        <van-field v-model="form.reheatMethod" label="复热方式" placeholder="微波 / 水浴" required />
        <van-field v-model="form.tasteScore" label="口味评分" placeholder="8.5 / 10" required />
        <van-field
          v-model="form.textureComment"
          rows="2"
          autosize
          type="textarea"
          label="口感评价"
          placeholder="鸡肉嫩度合适"
          required
        />
        <van-field v-model="form.waterRelease" label="出水情况" placeholder="轻微出水，可接受" />
      </div>

      <div class="section-title">测试结论</div>
      <ConclusionButtons v-model="conclusion" :options="conclusionOptions" />
      </template>
    </van-skeleton>

    <FixedActionBar v-if="!loadError" :with-tabbar="false">
      <van-button
        v-if="conclusion === 'PASS'"
        plain
        type="primary"
        block
        :loading="submitting && pendingAction === 'resample'"
        @click="submitResample"
      >
        不通过，复打样
      </van-button>
      <van-button
        type="primary"
        block
        :loading="submitting && pendingAction !== 'resample'"
        @click="submitPrimary"
      >
        {{ primaryButtonLabel }}
      </van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showConfirmDialog, showFailToast, showSuccessToast } from "vant";
import type { RndTaskDetailView } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import InfoCard from "../components/InfoCard.vue";
import ConclusionButtons from "../components/ConclusionButtons.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const pendingAction = ref<"pass" | "resample" | "stop">("pass");
const conclusion = ref("PASS");
const detail = ref<RndTaskDetailView | null>(null);
const loadError = ref("");
const form = reactive({
  reheatMethod: "",
  tasteScore: "",
  textureComment: "",
  waterRelease: "",
});

const conclusionOptions = [
  { value: "PASS", label: "通过", variant: "pass" as const },
  { value: "RESAMPLE", label: "复打样", variant: "resample" as const },
  { value: "STOP", label: "停止", variant: "stop" as const },
];

const headerTitle = computed(() => {
  if (!detail.value) return "测试确认";
  return `${detail.value.task.productName} ${detail.value.task.versionCode}`;
});

const experimentSummary = computed(() => detail.value?.currentExperimentForm?.summary || "暂无实验单摘要");

const primaryButtonLabel = computed(() => {
  if (conclusion.value === "STOP") return "提交停止建议";
  if (conclusion.value === "RESAMPLE") return "不通过，复打样";
  return "测试通过并锁版";
});

function buildComment() {
  return [
    `复热方式：${form.reheatMethod}`,
    `口味评分：${form.tasteScore}`,
    `口感评价：${form.textureComment}`,
    form.waterRelease ? `出水情况：${form.waterRelease}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

function validateForm() {
  if (!form.reheatMethod || !form.tasteScore || !form.textureComment) {
    showFailToast("请填写完整测试项");
    return false;
  }
  return true;
}

async function submitPrimary() {
  if (!validateForm()) return;
  pendingAction.value = conclusion.value === "STOP" ? "stop" : "pass";
  if (conclusion.value === "STOP") {
    await submitStop();
    return;
  }
  if (conclusion.value === "RESAMPLE") {
    await submitResample();
    return;
  }
  await submitPass();
}

async function submitPass() {
  const testId = detail.value?.currentTestAssignment?.id;
  if (!testId) {
    showFailToast("未找到测试任务");
    return;
  }
  submitting.value = true;
  pendingAction.value = "pass";
  try {
    const testerName = auth.displayName;
    await api.task.passInternalTest(testId, testerName, buildComment());
    showSuccessToast("测试通过，版本已锁定");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

async function submitResample() {
  if (!validateForm()) return;
  const testId = detail.value?.currentTestAssignment?.id;
  if (!testId) {
    showFailToast("未找到测试任务");
    return;
  }
  submitting.value = true;
  pendingAction.value = "resample";
  try {
    const testerName = auth.displayName;
    await api.task.failInternalTest(testId, testerName, buildComment());
    showSuccessToast("已退回复打样");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

async function submitStop() {
  const testId = detail.value?.currentTestAssignment?.id;
  if (!testId) {
    showFailToast("未找到测试任务");
    return;
  }
  await showConfirmDialog({
    title: "建议停止",
    message: "内部测试建议停止后，请由研发总监在 PC 端停止/废弃项目池确认。当前仅记录测试意见。",
  });
  submitting.value = true;
  pendingAction.value = "stop";
  try {
    await api.task.failInternalTest(testId, auth.displayName, `[建议停止]\n${buildComment()}`);
    showSuccessToast("已记录停止建议");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
  } catch (error) {
    detail.value = null;
    loadError.value = error instanceof Error ? error.message : "测试任务加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
