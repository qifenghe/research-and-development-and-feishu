import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const DEFAULT_ENV_FILE = "backend/.env.feishu.local";
const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const REQUIRED_ENV_KEYS = [
  "FEISHU_MODE",
  "FEISHU_APP_ID",
  "FEISHU_APP_SECRET",
  "FEISHU_APP_URL",
  "FEISHU_CARD_ACTION_SECRET",
];

export function parseEnvText(text) {
  const result = {};
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const equalsIndex = line.indexOf("=");
    if (equalsIndex < 0) {
      continue;
    }
    const key = line.slice(0, equalsIndex).trim();
    const value = stripQuotes(line.slice(equalsIndex + 1).trim());
    result[key] = value;
  }
  return result;
}

export function evaluateEnv(env) {
  const missing = REQUIRED_ENV_KEYS.filter((key) => isBlankOrPlaceholder(env[key]));
  if (env.FEISHU_MODE && env.FEISHU_MODE !== "OPENAPI") {
    missing.push("FEISHU_MODE=OPENAPI");
  }
  return {
    missing,
    ready: missing.length === 0,
  };
}

export function evaluateIntegrationStatus(status) {
  const missing = [];
  if (status.mode !== "OPENAPI") {
    missing.push("mode=OPENAPI");
  }
  if (status.appIdConfigured !== true) {
    missing.push("appIdConfigured=true");
  }
  if (status.appSecretConfigured !== true) {
    missing.push("appSecretConfigured=true");
  }
  if (status.readyForOpenApi !== true) {
    missing.push("readyForOpenApi=true");
  }
  return {
    missing,
    ready: missing.length === 0,
  };
}

export function buildReadinessAdvice(result) {
  if (!result.envResult?.ready) {
    return {
      code: "FILL_ENV",
      message: "请先补齐 backend/.env.feishu.local 中的飞书配置。",
    };
  }
  if (result.requestError) {
    return {
      code: "START_BACKEND",
      message: "配置已填写，请先运行 node scripts/run-backend-local.mjs 启动后端。",
    };
  }
  if (!result.statusResult?.ready) {
    return {
      code: "RESTART_WITH_ENV",
      message: "后端已启动，但飞书 OpenAPI 未就绪，请用 node scripts/run-backend-local.mjs 重新启动。",
    };
  }
  return {
    code: "READY_TO_DEMO",
    message: "飞书 OpenAPI 已就绪，可以运行 node scripts/feishu-demo-task.mjs 生成演示任务。",
  };
}

export async function requestIntegrationStatus(baseUrl) {
  const url = new URL("/api/v1/feishu/integration/status", ensureTrailingSlash(baseUrl));
  const body = await requestText(url);
  return parseIntegrationStatusResponse(body);
}

export function parseIntegrationStatusResponse(body) {
  let payload;
  try {
    payload = JSON.parse(body);
  } catch (error) {
    throw new Error(`集成状态响应不是 JSON：${error.message}`);
  }
  if (payload.code !== "0") {
    throw new Error(`集成状态接口返回失败：${payload.code || "UNKNOWN"} ${payload.message || ""}`.trim());
  }
  return payload.data;
}

export async function runCheck(options = {}) {
  const cwd = options.cwd || process.cwd();
  const envFile = path.resolve(cwd, options.envFile || DEFAULT_ENV_FILE);
  const backendUrl = options.backendUrl || process.env.BACKEND_URL || DEFAULT_BACKEND_URL;

  const envText = fs.readFileSync(envFile, "utf8");
  const env = parseEnvText(envText);
  const envResult = evaluateEnv(env);
  let status = null;
  let statusResult = null;
  let requestError = null;

  if (envResult.ready) {
    try {
      status = await requestIntegrationStatus(backendUrl);
      statusResult = evaluateIntegrationStatus(status);
    } catch (error) {
      requestError = error;
    }
  }

  return {
    envFile,
    backendUrl,
    env,
    envResult,
    status,
    statusResult,
    requestError,
    ready: envResult.ready && statusResult?.ready === true,
  };
}

function stripQuotes(value) {
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  return value;
}

function isBlankOrPlaceholder(value) {
  if (!value || !value.trim()) {
    return true;
  }
  return /请填写|change-me|xxxxxxxx|your-/.test(value);
}

function ensureTrailingSlash(baseUrl) {
  return baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
}

function requestText(url) {
  const transport = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const request = transport.get(url, { timeout: 5000 }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`HTTP ${response.statusCode}: ${body.slice(0, 200)}`));
          return;
        }
        resolve(body);
      });
    });
    request.on("timeout", () => {
      request.destroy(new Error(`请求超时：${url.href}`));
    });
    request.on("error", reject);
  });
}

function printReport(result) {
  console.log("飞书本地联调自检");
  console.log(`- 配置文件：${result.envFile}`);
  console.log(`- 后端地址：${result.backendUrl}`);
  console.log(`- FEISHU_MODE：${result.env.FEISHU_MODE || "(未配置)"}`);
  console.log(`- FEISHU_APP_ID：${describeVisibleValue(result.env.FEISHU_APP_ID)}`);
  console.log(`- FEISHU_APP_SECRET：${describeSecretValue(result.env.FEISHU_APP_SECRET)}`);
  console.log(`- FEISHU_APP_URL：${result.env.FEISHU_APP_URL || "(未配置)"}`);

  if (!result.envResult.ready) {
    console.log("\n配置检查：未通过");
    console.log(`需要补齐：${result.envResult.missing.join(", ")}`);
    printAdvice(result);
    process.exitCode = 1;
    return;
  }

  console.log("\n配置检查：通过");

  if (result.requestError) {
    console.log("\n后端状态检查：未通过");
    console.log(`原因：${result.requestError.message}`);
    console.log("提示：请先启动后端，并确认端口和 BACKEND_URL 是否正确。");
    printAdvice(result);
    process.exitCode = 1;
    return;
  }

  console.log("\n后端状态检查：通过");
  console.log(`- mode：${result.status.mode}`);
  console.log(`- baseUrl：${result.status.baseUrl}`);
  console.log(`- appIdConfigured：${result.status.appIdConfigured}`);
  console.log(`- appSecretConfigured：${result.status.appSecretConfigured}`);
  console.log(`- readyForOpenApi：${result.status.readyForOpenApi}`);

  if (!result.statusResult.ready) {
    console.log("\nOpenAPI 就绪检查：未通过");
    console.log(`需要满足：${result.statusResult.missing.join(", ")}`);
    printAdvice(result);
    process.exitCode = 1;
    return;
  }

  console.log("\nOpenAPI 就绪检查：通过，可以进入真实飞书通知派发联调。");
  printAdvice(result);
}

function mask(value) {
  if (!value) {
    return "(未配置)";
  }
  if (value.length <= 8) {
    return "***";
  }
  return `${value.slice(0, 6)}***${value.slice(-4)}`;
}

function describeVisibleValue(value) {
  return isBlankOrPlaceholder(value) ? "(未配置或占位符)" : mask(value);
}

function describeSecretValue(value) {
  return isBlankOrPlaceholder(value) ? "(未配置或占位符)" : "***已填写***";
}

function printAdvice(result) {
  const advice = buildReadinessAdvice(result);
  console.log(`\n下一步：${advice.message}`);
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[++index];
    } else if (arg === "--backend-url") {
      options.backendUrl = argv[++index];
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    }
  }
  return options;
}

function printHelp() {
  console.log(`用法：
  node scripts/feishu-local-check.mjs [--env-file backend/.env.feishu.local] [--backend-url http://127.0.0.1:8080]

说明：
  - 只检查配置是否完整和后端 /api/v1/feishu/integration/status 是否 ready。
  - 不打印 App Secret。
  - 默认读取 backend/.env.feishu.local，该文件不会提交到 GitHub。`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    runCheck(options)
      .then(printReport)
      .catch((error) => {
        console.error(`飞书本地联调自检失败：${error.message}`);
        process.exitCode = 1;
      });
  }
}
