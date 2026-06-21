import assert from "node:assert/strict";

import {
  buildReadinessAdvice,
  evaluateEnv,
  evaluateFeishuAppUrl,
  evaluateIntegrationStatus,
  parseEnvText,
  parseIntegrationStatusResponse,
} from "./feishu-local-check.mjs";

const parsed = parseEnvText(`
# comment
FEISHU_MODE=OPENAPI
FEISHU_APP_ID=cli_test_123
FEISHU_APP_SECRET="secret value"
FEISHU_APP_URL=https://rnd.example.com/
FEISHU_CARD_ACTION_SECRET=card-secret
`);

assert.equal(parsed.FEISHU_MODE, "OPENAPI");
assert.equal(parsed.FEISHU_APP_SECRET, "secret value");

assert.deepEqual(evaluateEnv(parsed).missing, []);
assert.deepEqual(evaluateFeishuAppUrl("https://rnd.example.com/").warnings, []);
assert.deepEqual(evaluateFeishuAppUrl("http://127.0.0.1:4174/").warnings, [
  "FEISHU_APP_URL 建议使用手机可访问的 HTTPS 地址，否则飞书卡片链接可能打不开",
]);
assert.deepEqual(evaluateFeishuAppUrl("not a url").warnings, [
  "FEISHU_APP_URL 不是合法 URL",
]);
assert.deepEqual(evaluateEnv({
  FEISHU_MODE: "OPENAPI",
  FEISHU_APP_ID: "请填写你的飞书AppID",
  FEISHU_APP_SECRET: "",
}).missing, ["FEISHU_APP_ID", "FEISHU_APP_SECRET", "FEISHU_APP_URL", "FEISHU_CARD_ACTION_SECRET"]);

assert.deepEqual(evaluateIntegrationStatus({
  mode: "OPENAPI",
  appIdConfigured: true,
  appSecretConfigured: true,
  readyForOpenApi: true,
}).missing, []);

assert.deepEqual(evaluateIntegrationStatus({
  mode: "MOCK",
  appIdConfigured: false,
  appSecretConfigured: true,
  readyForOpenApi: false,
}).missing, ["mode=OPENAPI", "appIdConfigured=true", "readyForOpenApi=true"]);

const status = parseIntegrationStatusResponse(JSON.stringify({
  code: "0",
  message: "success",
  data: {
    mode: "OPENAPI",
    baseUrl: "https://open.feishu.cn",
    appIdConfigured: true,
    appSecretConfigured: true,
    readyForOpenApi: true,
  },
}));
assert.equal(status.readyForOpenApi, true);

assert.deepEqual(buildReadinessAdvice({
  envResult: { ready: false, missing: ["FEISHU_APP_ID"] },
}), {
  code: "FILL_ENV",
  message: "请先补齐 backend/.env.feishu.local 中的飞书配置。",
});

assert.deepEqual(buildReadinessAdvice({
  envResult: { ready: true, missing: [] },
  requestError: new Error("connect ECONNREFUSED"),
}), {
  code: "START_BACKEND",
  message: "配置已填写，请先运行 node scripts/run-backend-local.mjs 启动后端。",
});

assert.deepEqual(buildReadinessAdvice({
  envResult: { ready: true, missing: [] },
  statusResult: { ready: false, missing: ["mode=OPENAPI"] },
}), {
  code: "RESTART_WITH_ENV",
  message: "后端已启动，但飞书 OpenAPI 未就绪，请用 node scripts/run-backend-local.mjs 重新启动。",
});

assert.deepEqual(buildReadinessAdvice({
  envResult: { ready: true, missing: [] },
  statusResult: { ready: true, missing: [] },
}), {
  code: "READY_TO_DEMO",
  message: "飞书 OpenAPI 已就绪，可以进入真实飞书通知派发联调；完整演示数据脚本仍需要 MOCK 模式或真实免登链路。",
});

console.log("Feishu local check tests passed.");
