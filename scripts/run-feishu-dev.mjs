import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawn, spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";

import { buildFeishuConfigGuide, printFeishuConfigGuide } from "./print-feishu-config.mjs";
import { parseEnvText } from "./feishu-local-check.mjs";

const ROOT = process.cwd();
const ENV_FILE = path.join(ROOT, "backend/.env.feishu.local");
const ENV_EXAMPLE = path.join(ROOT, "backend/.env.feishu.example");
const GATEWAY_PORT = 8787;
const BACKEND_PORT = 8080;

function ensureEnvFile() {
  if (fs.existsSync(ENV_FILE)) {
    return;
  }
  if (!fs.existsSync(ENV_EXAMPLE)) {
    throw new Error("缺少 backend/.env.feishu.example，无法初始化本地配置。");
  }
  fs.copyFileSync(ENV_EXAMPLE, ENV_FILE);
  console.log(`已创建 ${ENV_FILE}，请填写飞书 App ID / Secret 后重新运行。`);
}

function commandExists(name) {
  return spawnSync("which", [name], { stdio: "ignore" }).status === 0;
}

function spawnLogged(label, command, args, options = {}) {
  const child = spawn(command, args, {
    cwd: ROOT,
    stdio: "inherit",
    ...options,
  });
  child.on("exit", (code) => {
    if (code && code !== 0) {
      console.error(`[${label}] 退出，code=${code}`);
    }
  });
  return child;
}

function readEnvValue(key) {
  if (!fs.existsSync(ENV_FILE)) return "";
  const env = parseEnvText(fs.readFileSync(ENV_FILE, "utf8"));
  return env[key] || "";
}

function updateEnvAppUrl(publicUrl) {
  if (!fs.existsSync(ENV_FILE) || !publicUrl) return;
  const text = fs.readFileSync(ENV_FILE, "utf8");
  const root = publicUrl.endsWith("/") ? publicUrl.slice(0, -1) : publicUrl;
  const next = text.includes("FEISHU_APP_URL=")
    ? text.replace(/^FEISHU_APP_URL=.*$/m, `FEISHU_APP_URL=${root}`)
    : `${text.trim()}\nFEISHU_APP_URL=${root}\n`;
  fs.writeFileSync(ENV_FILE, next);
  console.log(`已写入 FEISHU_APP_URL=${root}`);
}


function waitForPortFree(port, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      const inUse = spawnSync("lsof", ["-i", `:${port}`, "-sTCP:LISTEN"], { stdio: "ignore" }).status === 0;
      if (!inUse) {
        resolve();
        return;
      }
      if (Date.now() - started > timeoutMs) {
        reject(new Error(`端口 ${port} 仍被占用，无法重启后端`));
        return;
      }
      setTimeout(tick, 300);
    };
    tick();
  });
}
function startBackend() {
  return spawnLogged(
    "backend",
    "node",
    ["scripts/run-backend-local.mjs", "--port", String(BACKEND_PORT)],
    { env: process.env },
  );
}

async function main() {
  ensureEnvFile();

  console.log("启动飞书联调开发栈...\n");

  let backend = startBackend();
  let backendAppUrl = readEnvValue("FEISHU_APP_URL");

  async function restartBackendForAppUrl() {
    const previous = backend;
    await new Promise((resolve) => {
      previous.once("exit", resolve);
      previous.kill("SIGTERM");
    });
    await waitForPortFree(BACKEND_PORT);
    backend = startBackend();
    backendAppUrl = readEnvValue("FEISHU_APP_URL");
  }

  const pc = spawnLogged("pc", "pnpm", ["--dir", "frontend", "dev:pc"], { env: process.env });
  const mobile = spawnLogged("mobile", "pnpm", ["--dir", "frontend", "dev:mobile"], { env: process.env });

  await sleep(3000);

  const gateway = spawnLogged(
    "gateway",
    "node",
    [
      "scripts/dev-gateway.mjs",
      "--port",
      String(GATEWAY_PORT),
      "--host",
      "0.0.0.0",
      "--backend",
      `http://127.0.0.1:${BACKEND_PORT}`,
    ],
    { env: process.env },
  );

  let tunnelUrl = readEnvValue("FEISHU_APP_URL");
  let tunnelProcess = null;
  let configGuidePrinted = false;
  if (commandExists("cloudflared")) {
    console.log("\n检测到 cloudflared，正在创建 HTTPS 隧道...\n");
    tunnelProcess = spawn("cloudflared", ["tunnel", "--url", `http://127.0.0.1:${GATEWAY_PORT}`], {
      cwd: ROOT,
      stdio: ["ignore", "pipe", "pipe"],
    });

    tunnelProcess.stdout.setEncoding("utf8");
    tunnelProcess.stderr.setEncoding("utf8");
    const handleTunnelOutput = (chunk) => {
      process.stderr.write(chunk);
      const match = chunk.match(/https:\/\/(?!api\.)[-a-z0-9]+\.trycloudflare\.com/i);
      if (match) {
        const nextUrl = match[0];
        const normalized = nextUrl.endsWith("/") ? nextUrl.slice(0, -1) : nextUrl;
        const prev = (backendAppUrl || readEnvValue("FEISHU_APP_URL") || "").replace(/\/$/, "");
        if (normalized === prev) {
          tunnelUrl = normalized;
          if (!configGuidePrinted) {
            configGuidePrinted = true;
            printFeishuConfigGuide(tunnelUrl);
            console.log("请把上面清单中的地址配置到飞书开放平台，然后运行：");
            console.log("  node scripts/feishu-local-check.mjs\n");
          }
          return;
        }
        tunnelUrl = normalized;
        updateEnvAppUrl(tunnelUrl);
        backendAppUrl = tunnelUrl;
        console.log("\n隧道地址已更新，正在重启后端以加载 FEISHU_APP_URL...\n");
        void restartBackendForAppUrl().catch((error) => {
          console.error(error.message);
        });
        configGuidePrinted = true;
        printFeishuConfigGuide(tunnelUrl);
        console.log("请把上面清单中的地址配置到飞书开放平台，然后运行：");
        console.log("  node scripts/feishu-local-check.mjs\n");
      }
    };
    tunnelProcess.stdout.on("data", handleTunnelOutput);
    tunnelProcess.stderr.on("data", handleTunnelOutput);
  } else {
    console.log("\n未检测到 cloudflared。本地可先访问：");
    console.log(`  http://127.0.0.1:${GATEWAY_PORT}/admin/dashboard`);
    console.log(`  http://127.0.0.1:${GATEWAY_PORT}/m/todo`);
    const { spawnSync } = await import("node:child_process");
    spawnSync("node", ["scripts/print-lan-urls.mjs"], { cwd: ROOT, stdio: "inherit" });
    console.log("\n飞书真实联调需要 HTTPS，请安装 cloudflared 后重试：");
    console.log("  brew install cloudflared");
    console.log("\n或手动把 FEISHU_APP_URL 改成你的 HTTPS 域名后运行：");
    console.log("  node scripts/print-feishu-config.mjs https://your-domain.example.com\n");
    printFeishuConfigGuide(tunnelUrl || `http://127.0.0.1:${GATEWAY_PORT}`);
  }

  const shutdown = () => {
    for (const child of [backend, pc, mobile, gateway, tunnelProcess].filter(Boolean)) {
      child.kill("SIGTERM");
    }
    process.exit(0);
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
