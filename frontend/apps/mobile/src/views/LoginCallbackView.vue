<template>
  <div class="callback-page">
    <van-loading size="24px">{{ tip }}</van-loading>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast } from "vant";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const tip = ref("正在登录...");

onMounted(async () => {
  const code = typeof route.query.code === "string" ? route.query.code : "";
  if (!code) {
    showFailToast("缺少飞书授权 code");
    router.replace({ name: "login" });
    return;
  }
  try {
    await auth.loginWithFeishuCode(code);
    const redirect = cleanRedirect(typeof route.query.redirect === "string" ? route.query.redirect : "/todo");
    router.replace(redirect);
  } catch (error) {
    const message = error instanceof Error ? error.message : "登录失败";
    if (error instanceof ApiError && error.code === "FEISHU_USER_NOT_BOUND") {
      showFailToast(error.message || "飞书账号尚未绑定系统");
    } else if (error instanceof ApiError && error.code === "FEISHU_OAUTH_USER_FETCH_FAILED") {
      showFailToast("飞书授权失败，请从飞书重新打开");
    } else {
      showFailToast(message);
    }
    router.replace({ name: "login", query: { error: message } });
  }
});

function cleanRedirect(value: string) {
  if (!value || value.startsWith("//") || /^https?:\/\//i.test(value)) {
    return "/todo";
  }
  return value.startsWith("/") ? value : "/todo";
}
</script>

<style scoped>
.callback-page {
  align-items: center;
  display: flex;
  justify-content: center;
  min-height: 60vh;
}
</style>
