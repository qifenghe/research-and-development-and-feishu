import assert from "node:assert/strict";

import {
  evaluateEnv,
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

console.log("Feishu local check tests passed.");
