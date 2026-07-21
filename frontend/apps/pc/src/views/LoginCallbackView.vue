<template>
  <div class="callback-page">
    <a-spin :spinning="true" :tip="tip" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const tip = ref("正在登录...");

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

onMounted(async () => {
  const code = typeof route.query.code === "string" ? route.query.code : "";
  if (!code) {
    message.error("缺少飞书授权 code");
    router.replace({ name: "login" });
    return;
  }
  try {
    await auth.loginWithFeishuCode(code);
    await router.replace(resolveInternalRedirect(route.query.redirect));
  } catch (error) {
    if (error instanceof ApiError && error.code === "FEISHU_USER_NOT_BOUND") {
      message.error("飞书账号尚未绑定系统，请联系管理员执行 feishu-bind-users 脚本");
    } else if (error instanceof ApiError && error.code === "FEISHU_OAUTH_USER_FETCH_FAILED") {
      message.error("飞书授权失败，请从飞书客户端重新打开应用，勿使用 MOCK 登录");
    } else {
      message.error(error instanceof Error ? error.message : "登录失败");
    }
    router.replace({ name: "login" });
  }
});
</script>

<style scoped>
.callback-page {
  align-items: center;
  display: flex;
  justify-content: center;
  min-height: 100vh;
}
</style>
