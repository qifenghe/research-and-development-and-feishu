<template>
  <div class="login-page" data-testid="login-office-shell">
    <header class="login-brand">
      <span class="login-brand__mark">研</span>
      <div>
        <h1>研发样品管理</h1>
        <p>样品、测试与核价协同</p>
      </div>
    </header>

    <van-form @submit="login">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="账号"
          placeholder="例如 rnd_assistant"
          autocomplete="username"
          :rules="[{ required: true, message: '请输入账号' }]"
        />
        <van-field
          v-model="form.password"
          name="password"
          label="密码"
          type="password"
          placeholder="默认测试密码 123456"
          autocomplete="current-password"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>

      <p v-if="loginError" class="login-error" role="alert">{{ loginError }}</p>

      <div class="login-actions">
        <van-button type="primary" block native-type="submit" :loading="auth.loading">登录</van-button>
      </div>
    </van-form>

    <van-collapse v-model="expandedHelpers" inset class="test-helpers">
      <van-collapse-item title="测试辅助" name="test-helpers">
        <van-cell
          v-for="account in accounts"
          :key="account.username"
          is-link
          :title="account.label"
          :label="`${account.name} · ${account.username}`"
          @click="fill(account.username)"
        />
      </van-collapse-item>
    </van-collapse>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();

const form = reactive({
  username: "rnd_assistant",
  password: "123456",
});

const expandedHelpers = ref<string[]>([]);
const loginError = ref("");

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

async function login() {
  loginError.value = "";
  try {
    await auth.loginWithPassword(form.username.trim(), form.password);
    showSuccessToast("登录成功");
    await router.replace("/todo");
  } catch (error) {
    if (error instanceof ApiError && error.code === "AUTH_CREDENTIAL_INVALID") {
      loginError.value = "账号或密码不正确";
      showFailToast(loginError.value);
      return;
    }
    if (error instanceof ApiError && error.status === 401) {
      loginError.value = "登录已过期，请重新登录";
      showFailToast(loginError.value);
      return;
    }
    if (error instanceof TypeError || (error instanceof Error && /fetch failed/i.test(error.message))) {
      loginError.value = `无法连接服务，请确认电脑服务已启动且手机与电脑在同一网络（${location.host}）`;
      showFailToast(loginError.value);
      return;
    }
    loginError.value = error instanceof Error ? error.message : "登录失败";
    showFailToast(loginError.value);
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 36px 16px 24px;
}

.login-brand {
  align-items: center;
  display: flex;
  gap: 10px;
  margin: 0 0 24px;
}

.login-brand__mark {
  align-items: center;
  background: var(--mobile-primary);
  border-radius: var(--mobile-radius);
  color: #fff;
  display: inline-flex;
  font-size: 18px;
  font-weight: 700;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.login-brand h1 {
  color: var(--mobile-text);
  font-size: 22px;
  line-height: 1.2;
  margin: 0;
}

.login-brand p {
  color: var(--mobile-muted);
  font-size: 13px;
  margin: 4px 0 0;
}

.login-actions {
  padding: 16px 0 0;
}

.login-error {
  color: var(--mobile-danger);
  font-size: 13px;
  line-height: 1.45;
  margin: 12px 0 0;
}

.test-helpers {
  margin-top: 16px;
}
</style>
