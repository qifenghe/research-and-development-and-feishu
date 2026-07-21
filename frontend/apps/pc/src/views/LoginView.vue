<template>
  <div class="login-page">
    <a-card title="研发样品管理系统登录" class="login-card">
      <a-alert
        type="info"
        show-icon
        message="当前为网页端账号登录"
        description="飞书免登录入口暂时隐藏，后期上线飞书时再作为外部身份绑定。"
        style="margin-bottom: 16px"
      />

      <a-form layout="vertical" :model="form" @finish="login">
        <a-form-item label="账号" name="username" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="form.username" placeholder="例如 rnd_assistant" autocomplete="username" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="form.password" placeholder="默认测试密码 123456" autocomplete="current-password" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="auth.loading">登录</a-button>
      </a-form>

      <a-divider>测试账号</a-divider>
      <div class="quick-accounts">
        <a-button
          v-for="account in accounts"
          :key="account.username"
          block
          class="quick-account"
          @click="fill(account.username)"
        >
          {{ account.label }} · {{ account.name }}
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const form = reactive({
  username: "rnd_assistant",
  password: "123456",
});

const accounts = [
  { label: "研发内勤", name: "赵内勤", username: "rnd_assistant" },
  { label: "研发总监", name: "赵总监", username: "rnd_director" },
  { label: "研发人员", name: "张研发", username: "rnd_engineer" },
  { label: "内部测试", name: "李测试", username: "tester" },
  { label: "财务", name: "钱财务", username: "finance" },
];

function fill(username: string) {
  form.username = username;
  form.password = "123456";
}

function resolveInternalRedirect(value: unknown): string {
  if (typeof value !== "string") return "/dashboard";
  const trimmed = value.trim();
  if (!trimmed || !trimmed.startsWith("/") || trimmed.startsWith("//")) {
    return "/dashboard";
  }
  if (trimmed.startsWith("/admin/")) {
    return trimmed.slice("/admin".length) || "/dashboard";
  }
  return trimmed;
}

async function login() {
  try {
    await auth.loginWithPassword(form.username.trim(), form.password);
    message.success("登录成功");
    await router.replace(resolveInternalRedirect(route.query.redirect));
  } catch (error) {
    if (error instanceof ApiError && error.code === "AUTH_CREDENTIAL_INVALID") {
      message.error("账号或密码不正确");
      return;
    }
    message.error(error instanceof Error ? error.message : "登录失败");
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

.login-card {
  width: min(480px, 100%);
}

.quick-accounts {
  display: grid;
  gap: 8px;
}

.quick-account {
  text-align: left;
}
</style>
