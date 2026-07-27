<template>
  <div>
    <a-page-header
      :title="headerTitle"
      sub-title="内部测试评价单：口味、口感、出水、复热与测试结论"
      @back="router.back()"
    />

    <a-spin :spinning="loading">
      <a-row :gutter="16">
        <a-col :span="16">
          <a-card title="实验单摘要" class="page-card">
            <a-typography-paragraph>{{ experimentSummary }}</a-typography-paragraph>
          </a-card>

          <a-card title="内部测试评价" class="page-card">
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="复热方式" required>
                    <a-input v-model:value="form.reheatMethod" placeholder="微波 / 水浴 / 蒸柜" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="口味评分（满分10）" required>
                    <a-input v-model:value="form.tasteScore" placeholder="例如 8.5" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="口感评价" required>
                <a-textarea v-model:value="form.textureComment" :rows="3" placeholder="嫩度、纤维感、咀嚼感等" />
              </a-form-item>
              <a-form-item label="出水情况">
                <a-input v-model:value="form.waterRelease" placeholder="轻微出水，可接受" />
              </a-form-item>
              <a-form-item label="外观色泽">
                <a-input v-model:value="form.appearance" placeholder="色泽正常、无发黑等" />
              </a-form-item>
            </a-form>
          </a-card>
        </a-col>

        <a-col :span="8">
          <a-card title="测试结论" class="page-card">
            <a-radio-group v-model:value="conclusion" style="width: 100%">
              <a-space direction="vertical" style="width: 100%">
                <a-radio-button value="PASS" style="width: 100%; text-align: center">通过并锁版</a-radio-button>
                <a-radio-button value="RESAMPLE" style="width: 100%; text-align: center">不通过，复打样</a-radio-button>
                <a-radio-button value="STOP" style="width: 100%; text-align: center">建议停止打样</a-radio-button>
              </a-space>
            </a-radio-group>
            <a-button type="primary" block style="margin-top: 16px" :loading="submitting" @click="submitResult">
              提交内部测试评价
            </a-button>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Modal, message } from "ant-design-vue";
import type { RndTaskDetailView } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

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
  appearance: "",
});

const headerTitle = computed(() => {
  if (!detail.value) return "内部测试评价单";
  return `${detail.value.task.productName} ${detail.value.task.versionCode}`;
});

const experimentSummary = computed(() => detail.value?.currentExperimentForm?.summary || "暂无实验单摘要");

function buildComment() {
  return [
    `复热方式：${form.reheatMethod}`,
    `口味评分：${form.tasteScore}`,
    `口感评价：${form.textureComment}`,
    form.waterRelease ? `出水情况：${form.waterRelease}` : "",
    form.appearance ? `外观色泽：${form.appearance}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

async function submitResult() {
  if (!form.reheatMethod || !form.tasteScore || !form.textureComment) {
    message.warning("请填写完整测试评价项");
    return;
  }

  const testId = detail.value?.currentTestAssignment?.id;
  if (!testId) {
    message.error("未找到内部测试任务");
    return;
  }

  if (conclusion.value === "STOP") {
    Modal.confirm({
      title: "建议停止打样",
      content: "将记录停止建议并进入复打样/废弃流程处理，是否继续？",
      onOk: async () => {
        submitting.value = true;
        try {
          await api.task.failInternalTest(testId, auth.displayName, `[建议停止]\n${buildComment()}`);
          message.success("已记录停止建议");
          router.push(`/rnd/tasks/${route.params.id}`);
        } catch (error) {
          message.error(error instanceof Error ? error.message : "提交失败");
        } finally {
          submitting.value = false;
        }
      },
    });
    return;
  }

  submitting.value = true;
  try {
    const comment = buildComment();
    const testerName = auth.displayName;
    if (conclusion.value === "PASS") {
      await api.task.passInternalTest(testId, testerName, comment);
      message.success("测试通过，实验单已锁定");
    } else {
      await api.task.failInternalTest(testId, testerName, comment);
      message.success("已退回复打样，将生成下一版本");
    }
    router.push(`/rnd/tasks/${route.params.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
  } finally {
    loading.value = false;
  }
});
</script>
