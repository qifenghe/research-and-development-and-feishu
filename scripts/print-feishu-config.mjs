import fs from "node:fs";
import path from "node:path";
import { parseEnvText } from "./feishu-local-check.mjs";

function readAppUrlFromEnv() {
  const envFile = path.join(process.cwd(), "backend/.env.feishu.local");
  if (!fs.existsSync(envFile)) return "";
  return parseEnvText(fs.readFileSync(envFile, "utf8")).FEISHU_APP_URL || "";
}


export function buildFeishuConfigGuide(baseUrl) {
  const root = normalizeRoot(baseUrl);
  return {
    root,
    env: {
      FEISHU_APP_URL: root,
    },
    feishuConsole: {
      desktopHome: `${root}/admin/dashboard`,
      mobileHome: `${root}/m/todo`,
      redirectUrls: [
        `${root}/admin/login`,
        `${root}/admin/login/callback`,
        `${root}/m/login`,
        `${root}/m/login/callback`,
      ],
      cardActionCallback: `${root}/api/v1/feishu/card-actions`,
      oauthCallbackApi: `${root}/api/v1/feishu/oauth/callback`,
    },
    messageCardLinks: {
      rndTaskDetailMobile: `${root}/m/tasks/{taskId}`,
      pcDashboard: `${root}/admin/dashboard`,
    },
  };
}

function normalizeRoot(baseUrl) {
  const trimmed = (baseUrl || "https://your-tunnel.example.com").trim();
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

export function printFeishuConfigGuide(baseUrl) {
  printGuide(buildFeishuConfigGuide(baseUrl));
}

function printGuide(guide) {
  console.log("\n======== 飞书后台配置清单 ========");
  console.log(`统一网关根地址（写入 FEISHU_APP_URL）：\n  ${guide.root}\n`);
  console.log("【应用主页】");
  console.log(`  桌面端主页：${guide.feishuConsole.desktopHome}`);
  console.log(`  移动端主页：${guide.feishuConsole.mobileHome}\n`);
  console.log("【重定向 URL / 免登回调】（可添加多条）");
  for (const url of guide.feishuConsole.redirectUrls) {
    console.log(`  ${url}`);
  }
  console.log("\n【卡片按钮回调】");
  console.log(`  POST ${guide.feishuConsole.cardActionCallback}`);
  console.log("  请求头：X-Feishu-Card-Secret: <与 FEISHU_CARD_ACTION_SECRET 一致>\n");
  console.log("【后端 OAuth 接口】");
  console.log(`  POST ${guide.feishuConsole.oauthCallbackApi}`);
  console.log("\n【消息卡片跳转示例】");
  console.log(`  研发任务（手机）：${guide.messageCardLinks.rndTaskDetailMobile}`);
  console.log(`  默认工作台（PC）：${guide.messageCardLinks.pcDashboard}`);
  console.log("\n【backend/.env.feishu.local 关键项】");
  console.log(`  FEISHU_MODE=OPENAPI`);
  console.log(`  FEISHU_APP_URL=${guide.env.FEISHU_APP_URL}`);
  console.log("  FEISHU_APP_ID=cli_xxx");
  console.log("  FEISHU_APP_SECRET=你的 App Secret");
  console.log("  FEISHU_CARD_ACTION_SECRET=随机字符串");
  console.log("==================================\n");
}

if (process.argv[1]?.endsWith("print-feishu-config.mjs")) {
  const baseUrl = process.argv[2] || process.env.FEISHU_PUBLIC_URL || readAppUrlFromEnv() || "https://your-tunnel.example.com";
  printFeishuConfigGuide(baseUrl);
}
