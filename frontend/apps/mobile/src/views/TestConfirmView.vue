<template>
  <div>
    <van-skeleton title :row="5" :loading="loading">
      <PageHeader :title="headerTitle" compact>
        <StatusBadge label="待测试" variant="primary" />
      </PageHeader>

      <InfoCard title="实验单摘要">
        <div class="info-row">
          <span class="info-row__value" style="max-width: 100%; text-align: left">{{ experimentSummary }}</span>
        </div>
      </InfoCard>

      <van-field v-model="form.reheatMethod" label="复热方式" placeholder="微波 / 水浴" required />
      <van-field v-model="form.tasteScore" label="口味评分" placeholder="8.5 / 10" required />
      <van-field v-model="form.textureComment" rows="2" autosize type="textarea" label="口感评价" placeholder="鸡肉嫩度合适" required />
      <van-field v-model="form.waterRelease" label="出水情况" placeholder="轻微出水，可接受" />

      <div class="section-title">测试结论</div>
      <ConclusionButtons v-model="conclusion" :options="conclusionOptions" />
    </van-skeleton>

    <FixedActionBar>
      <van-button type="primary" block :loading="submitting" @click="submitResult">提交测试结果</van-button>
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
const conclusion = ref("PASS");
const detail = ref<RndTaskDetailView | null>(null);
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

async function submitResult() {
  if (!form.reheatMethod || !form.tasteScore || !form.textureComment) {
    showFailToast("请填写完整测试项");
    return;
  }

  const testId = detail.value?.currentTestAssignment?.id;
  if (!testId) {
    showFailToast("未找到测试任务");
    return;
  }

  if (conclusion.value === "STOP") {
    await showConfirmDialog({
      title: "建议停止",
      message: "内部测试建议停止后，请由研发总监在 PC 端停止/废弃项目池确认。当前仅记录测试意见。",
    });
    submitting.value = true;
    try {
      await api.task.failInternalTest(testId, auth.displayName, `[建议停止]\n${buildComment()}`);
      showSuccessToast("已记录停止建议");
      router.push("/todo");
    } catch (error) {
      showFailToast(error instanceof Error ? error.message : "提交失败");
    } finally {
      submitting.value = false;
    }
    return;
  }

  submitting.value = true;
  try {
    const comment = buildComment();
    const testerName = detail.value?.currentTestAssignment?.testerName || "内部测试员";
    if (conclusion.value === "PASS") {
      await api.task.passInternalTest(testId, testerName, comment);
      showSuccessToast("测试通过，版本已锁定");
    } else {
      await api.task.failInternalTest(testId, testerName, comment);
      showSuccessToast("已退回复打样");
    }
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
    detail.value = await api.task.detail(String(route.params.id), auth.role);
  } finally {
    loading.value = false;
  }
});
</script>
