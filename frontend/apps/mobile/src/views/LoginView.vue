<template>
  <div class="login-page">
    <h1>研发样品管理</h1>

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
  try {
    await auth.loginWithPassword(form.username.trim(), form.password);
    showSuccessToast("登录成功");
    await router.replace("/todo");
  } catch (error) {
    if (error instanceof ApiError && error.code === "AUTH_CREDENTIAL_INVALID") {
      showFailToast("账号或密码不正确");
      return;
    }
    if (error instanceof ApiError && error.status === 401) {
      showFailToast("登录已过期");
      return;
    }
    if (error instanceof TypeError || (error instanceof Error && /fetch failed/i.test(error.message))) {
      showFailToast(`无法连接服务，请确认电脑服务已启动且手机与电脑在同一网络（${location.host}）`);
      return;
    }
    showFailToast(error instanceof Error ? error.message : "登录失败");
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 28px 0;
}

.login-page h1 {
  color: #0f172a;
  font-size: 24px;
  margin: 0 24px 20px;
}

.login-actions {
  padding: 16px;
}

.test-helpers {
  margin-top: 8px;
}
</style>
