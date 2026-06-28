<template>
  <div class="login-page">
    <a-card title="PC 管理后台登录" style="width: 480px">
      <a-alert
        v-if="integration?.mode === 'OPENAPI'"
        type="info"
        show-icon
        message="当前为飞书真实联调模式"
        description="请从飞书客户端打开本应用完成免登。登录页 MOCK 按钮在 OPENAPI 模式下不可用。首次使用前需先绑定飞书 user_id。"
        style="margin-bottom: 16px"
      />
      <a-alert
        v-else
        type="success"
        show-icon
        message="当前为 MOCK 模式"
        description="可直接使用下方 MOCK 登录；user_id 需已在系统中绑定。"
        style="margin-bottom: 16px"
      />
      <p>飞书 OAuth 回调：<code>/admin/login/callback</code></p>
      <a-divider v-if="integration?.mode !== 'OPENAPI'">本地 MOCK 登录</a-divider>
      <div v-if="integration?.mode !== 'OPENAPI'" class="demo-roles">
        <a-button
          v-for="account in demoAccounts"
          :key="account.feishuUserId"
          block
          class="demo-role-button"
          :loading="auth.loading && feishuUserId === account.feishuUserId"
          @click="loginAs(account.feishuUserId)"
        >
          {{ account.label }} · {{ account.name }}
        </a-button>
      </div>
      <a-form v-if="integration?.mode !== 'OPENAPI'" layout="vertical" @finish="onMockLogin">
        <a-form-item label="飞书用户 ID" name="feishuUserId" :rules="[{ required: true, message: '请输入用户 ID' }]">
          <a-input v-model:value="feishuUserId" placeholder="例如 5a6d46c2" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="auth.loading">MOCK 登录</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const feishuUserId = ref("5a6d46c2");
const integration = ref<{ mode: string } | null>(null);
const demoAccounts = [
  { label: "研发内勤", name: "赵内勤", feishuUserId: "5a6d46c2" },
  { label: "研发总监", name: "赵总监", feishuUserId: "ou_demo_director" },
  { label: "研发人员", name: "张研发", feishuUserId: "ou_demo_engineer" },
  { label: "财务", name: "钱财务", feishuUserId: "ou_demo_finance" },
];

onMounted(async () => {
  try {
    integration.value = await fetch("/api/v1/feishu/integration/status").then((r) => r.json()).then((p) => p.data);
  } catch {
    integration.value = null;
  }
  const code = typeof route.query.code === "string" ? route.query.code : "";
  if (code) {
    router.replace({ name: "login-callback", query: { ...route.query } });
  }
});

async function onMockLogin() {
  await loginAs(feishuUserId.value.trim());
}

async function loginAs(userId: string) {
  try {
    feishuUserId.value = userId;
    await auth.mockLogin(userId);
    message.success("登录成功");
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    router.replace(redirect);
  } catch (error) {
    if (error instanceof ApiError && error.code === "FEISHU_USER_NOT_BOUND") {
      message.error("该 user_id 尚未绑定，请先运行 node scripts/feishu-bind-users.mjs");
    } else {
      message.error(error instanceof Error ? error.message : "登录失败");
    }
  }
}
</script>

<style scoped>
.login-page {
  align-items: center;
  background: linear-gradient(180deg, #eef4ff, #f8fafc);
  display: flex;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
}

.demo-roles {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.demo-role-button {
  text-align: left;
}
</style>
