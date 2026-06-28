<template>
  <div class="login-page">
    <van-notice-bar
      v-if="integrationError"
      type="danger"
      wrapable
      :scrollable="false"
      text="无法连接后端 API（502）。请确认 node scripts/run-feishu-dev.mjs 或 run-backend-local.mjs 已启动。"
    />
    <van-notice-bar
      v-if="integration?.mode === 'OPENAPI'"
      wrapable
      :scrollable="false"
      text="当前为飞书真实联调模式：请从飞书客户端打开应用。首次需先绑定 user_id。"
    />
    <van-notice-bar
      v-if="loginError"
      type="danger"
      wrapable
      :scrollable="false"
      :text="loginError"
    />
    <van-cell-group inset title="手机端登录">
      <van-field v-if="integration?.mode !== 'OPENAPI'" v-model="feishuUserId" label="飞书用户 ID" placeholder="例如 ou_demo_engineer" />
      <van-cell
        v-else
        title="飞书免登"
        :label="feishuLoginTip"
      />
      <van-cell
        v-if="integration?.mode === 'OPENAPI'"
        title="调试信息"
        :label="debugInfo"
      />
      <van-cell
        v-if="integration?.mode === 'OPENAPI'"
        title="前端版本"
        value="official-requestAccess-v3"
      />
      <van-cell
        v-if="integration?.mode === 'OPENAPI'"
        title="当前域名"
        :label="trustedDomainTip"
      />
    </van-cell-group>
    <div v-if="integration?.mode === 'OPENAPI'" style="padding: 16px">
      <van-button type="primary" block :loading="auth.loading || requestingFeishuCode" @click="requestFeishuCode">
        使用飞书免登（官方 requestAccess）
      </van-button>
    </div>
    <div v-if="integration?.mode !== 'OPENAPI'" style="padding: 16px">
      <van-cell-group inset style="margin-bottom: 16px">
        <van-cell title="演示版本" value="mock-direct-v1" />
      </van-cell-group>
      <van-cell-group inset title="选择演示角色" style="margin-bottom: 16px">
        <van-cell
          v-for="account in demoAccounts"
          :key="account.feishuUserId"
          is-link
          :title="account.label"
          :label="`${account.name} · ${account.feishuUserId}`"
          @click="loginAs(account.feishuUserId)"
        />
      </van-cell-group>
      <van-button type="primary" block :loading="auth.loading" @click="login">MOCK 登录</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import { ApiError } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const feishuUserId = ref("5a6d46c2");
const integration = ref<{ mode: string; appId?: string } | null>(null);
const integrationError = ref(false);
const requestingFeishuCode = ref(false);
const feishuLoginTip = ref("请在飞书客户端内点击按钮获取免登身份。");
const debugInfo = ref("等待检测飞书 JS SDK...");
const trustedDomainTip = ref("");
const loginError = ref("");
const demoAccounts = [
  { label: "研发内勤", name: "赵内勤", feishuUserId: "5a6d46c2" },
  { label: "研发总监", name: "赵总监", feishuUserId: "ou_demo_director" },
  { label: "研发人员", name: "张研发", feishuUserId: "ou_demo_engineer" },
  { label: "财务", name: "钱财务", feishuUserId: "ou_demo_finance" },
];

declare global {
  interface Window {
    h5sdk?: {
      ready?: (callback: () => void) => void;
      error?: (callback: (error: unknown) => void) => void;
    };
    tt?: {
      requestAccess?: (options: {
        appID: string;
        scopeList: string[];
        success?: (result: { code?: string }) => void;
        fail?: (error: unknown) => void;
      }) => void;
    };
  }
}

onMounted(async () => {
  if (route.name === "login") {
    auth.signOut();
  }
  loginError.value = typeof route.query.error === "string" ? route.query.error : "";
  try {
    const response = await fetch("/api/v1/feishu/integration/status");
    if (!response.ok) {
      integrationError.value = true;
      if (isFeishuWebView()) {
        integration.value = { mode: "OPENAPI" };
      }
      return;
    }
    integration.value = (await response.json()).data;
  } catch {
    integration.value = isFeishuWebView() ? { mode: "OPENAPI" } : null;
    integrationError.value = true;
  }
  const code = typeof route.query.code === "string" ? route.query.code : "";
  if (code) {
    try {
      await finishFeishuLogin(code);
    } catch (error) {
      presentLoginError(error);
    }
    return;
  }
  if (integration.value?.mode === "OPENAPI") {
    updateFeishuEnvironment();
    if (loginError.value) {
      feishuLoginTip.value = "上次登录失败，请检查上方错误后手动重试。";
      return;
    }
    if (!hasFeishuRequestAccess()) {
      feishuLoginTip.value = "未检测到可用的飞书免登能力，请从飞书客户端的应用入口打开。";
      return;
    }
    feishuLoginTip.value = "已检测到飞书免登能力，可以点击按钮获取身份。";
  }
});

async function login() {
  await loginAs(feishuUserId.value.trim());
}

async function loginAs(userId: string) {
  try {
    feishuUserId.value = userId;
    await auth.mockLogin(userId);
    showSuccessToast("登录成功");
    window.location.assign(`${window.location.origin}/m/todo`);
  } catch (error) {
    if (error instanceof ApiError && error.code === "FEISHU_USER_NOT_BOUND") {
      showFailToast("该 user_id 尚未绑定");
    } else {
      showFailToast(error instanceof Error ? error.message : "登录失败");
    }
  }
}

async function requestFeishuCode(silent = false) {
  if (route.query.error) {
    loginError.value = "";
  }
  updateFeishuEnvironment();
  if (!integration.value?.appId) {
    if (!silent) showFailToast("后端未返回飞书 App ID");
    return;
  }
  if (!hasFeishuRequestAccess()) {
    const message = "当前页面没有可用的飞书 requestAccess。请从飞书客户端应用入口打开，不要用外部浏览器。";
    loginError.value = message;
    feishuLoginTip.value = message;
    if (!silent) showFailToast(message);
    return;
  }
  requestingFeishuCode.value = true;
  feishuLoginTip.value = "正在调用飞书官方 requestAccess...";
  try {
    const code = await getFeishuAuthCode(integration.value.appId);
    debugInfo.value = `已获取 code：${mask(code)}`;
    await finishFeishuLogin(code);
  } catch (error) {
    const message = presentLoginError(error);
    if (!silent) {
      showFailToast(message || "飞书免登失败");
    }
  } finally {
    requestingFeishuCode.value = false;
  }
}

function getFeishuAuthCode(appId: string) {
  return new Promise<string>((resolve, reject) => {
    if (!window.h5sdk) {
      reject(new Error("invalid h5sdk，请在飞书客户端内打开应用"));
      return;
    }
    window.h5sdk.error?.((error) => {
      reject(new Error(`h5sdk error: ${safeJson(error)}`));
    });
    const request = () => {
      if (!window.tt?.requestAccess) {
        reject(new Error("当前环境没有飞书 requestAccess 免登能力"));
        return;
      }
      try {
        window.tt.requestAccess({
          appID: appId,
          scopeList: [],
          success: (response) => {
            if (response.code) {
              resolve(response.code);
            } else {
              reject(new Error("requestAccess 未返回 code"));
            }
          },
          fail: (error) => {
            reject(new Error(`requestAccess failed: ${safeJson(error)}`));
          },
        });
      } catch (error) {
        reject(new Error(`requestAccess synchronous error: ${safeJson(error)}`));
      }
    };
    if (window.h5sdk.ready) {
      window.h5sdk.ready(request);
    } else {
      reject(new Error("h5sdk.ready 不存在，请确认 JSSDK 是否正确加载"));
    }
  });
}

async function finishFeishuLogin(code: string) {
  feishuLoginTip.value = "已获取飞书 code，正在换取系统登录态...";
  await auth.loginWithFeishuCode(code);
  showSuccessToast("登录成功");
  await router.replace("/todo");
}

function presentLoginError(error: unknown) {
  const rawMessage = error instanceof Error ? error.message : String(error);
  const message = explainFeishuLoginError(rawMessage);
  if (error instanceof ApiError && error.code === "FEISHU_USER_NOT_BOUND") {
    feishuLoginTip.value = "飞书身份已获取，但还没有绑定系统账号。";
  } else if (error instanceof ApiError) {
    feishuLoginTip.value = "飞书身份已获取，系统登录失败。";
  } else {
    feishuLoginTip.value = message;
  }
  loginError.value = message;
  debugInfo.value = `${sdkDebugText()}；原始错误：${rawMessage}`;
  return message;
}

function cleanRedirect(value: string) {
  if (!value || value.startsWith("//") || /^https?:\/\//i.test(value)) {
    return "/todo";
  }
  return value.startsWith("/") ? value : "/todo";
}

function isFeishuWebView() {
  return /Lark|Feishu|LarkShell/i.test(navigator.userAgent);
}

function updateFeishuEnvironment() {
  debugInfo.value = sdkDebugText();
  trustedDomainTip.value = trustedDomainText();
}

function hasFeishuRequestAccess() {
  return Boolean(window.tt?.requestAccess);
}

function sdkDebugText() {
  return [
    `appId=${integration.value?.appId || "空"}`,
    `build=official-requestAccess-v3`,
    `h5sdk=${window.h5sdk ? "有" : "无"}`,
    `tt=${window.tt ? "有" : "无"}`,
    `requestAccess=${window.tt?.requestAccess ? "有" : "无"}`,
    `url=${window.location.href}`,
    `ua=${navigator.userAgent.slice(0, 80)}`,
  ].join("；");
}

function trustedDomainText() {
  const host = window.location.hostname;
  if (!host) return "未识别当前域名";
  const currentUrl = window.location.href.split("#")[0].split("?")[0];
  const redirectUrls = [
    `${window.location.origin}/m/login`,
    `${window.location.origin}/m/login/callback`,
  ].join("、");
  if (host.endsWith(".trycloudflare.com")) {
    return `请在飞书后台加入当前临时域名：H5 可信域名填 ${host}；重定向 URL 填 ${redirectUrls}。当前页面是 ${currentUrl}。Cloudflare 临时域名每次变化后都要重新加入并发布。`;
  }
  return `请确认飞书后台已加入：H5 可信域名 ${host}；重定向 URL ${redirectUrls}，并且应用已重新发布生效。当前页面是 ${currentUrl}。`;
}

function explainFeishuLoginError(message: string) {
  if (/20029|10235|10236|invalid redirect uri|redirecturis not config|expected pattern|string did not match/i.test(message)) {
    return `${trustedDomainText()} 当前错误属于飞书 H5 域名/redirect_uri 校验失败。`;
  }
  if (/requestAccess|requestAuthCode|invalid h5sdk|飞书客户端|没有飞书 requestAccess/i.test(message)) {
    return "未完成飞书免登：请从飞书客户端的自建应用入口打开，外部浏览器不能完成真实免登。";
  }
  return message;
}

function safeJson(value: unknown) {
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function mask(value: string) {
  if (value.length <= 8) return "****";
  return `${value.slice(0, 4)}****${value.slice(-4)}`;
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding-top: 24px;
}
</style>
