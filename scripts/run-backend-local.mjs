import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawn } from "node:child_process";
import { pathToFileURL } from "node:url";

import { parseEnvText } from "./feishu-local-check.mjs";

const DEFAULT_ENV_FILE = "backend/.env.feishu.local";
const DEFAULT_BACKEND_DIR = "backend";
const DEFAULT_JAVA_HOME = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home";

export function readBackendLocalEnv(envFile) {
  return parseEnvText(fs.readFileSync(envFile, "utf8"));
}

export function buildBackendEnvironment(options = {}) {
  const baseEnv = options.baseEnv || process.env;
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  return {
    ...baseEnv,
    ...readBackendLocalEnv(envFile),
  };
}

export function buildBackendCommand(options = {}) {
  const backendDir = options.backendDir || DEFAULT_BACKEND_DIR;
  const javaHome = options.javaHome || DEFAULT_JAVA_HOME;
  const args = ["spring-boot:run"];
  if (options.port) {
    args.push(`-Dspring-boot.run.arguments=--server.port=${options.port}`);
  }
  return {
    command: "mvn",
    args,
    cwd: backendDir,
    env: {
      ...process.env,
      JAVA_HOME: javaHome,
    },
  };
}

export function parseRunBackendArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[++index];
    } else if (arg === "--backend-dir") {
      options.backendDir = argv[++index];
    } else if (arg === "--java-home") {
      options.javaHome = argv[++index];
    } else if (arg === "--port") {
      options.port = argv[++index];
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    }
  }
  return options;
}

function runBackend(options) {
  const cwd = process.cwd();
  const envFile = path.resolve(cwd, options.envFile || DEFAULT_ENV_FILE);
  const backendDir = path.resolve(cwd, options.backendDir || DEFAULT_BACKEND_DIR);
  const localEnv = buildBackendEnvironment({ envFile });
  const command = buildBackendCommand({
    backendDir,
    javaHome: options.javaHome || DEFAULT_JAVA_HOME,
    port: options.port,
  });
  const childEnv = {
    ...command.env,
    ...localEnv,
  };

  console.log("启动研发样品管理后端");
  console.log(`- env 文件：${envFile}`);
  console.log(`- 后端目录：${backendDir}`);
  console.log(`- FEISHU_MODE：${childEnv.FEISHU_MODE || "(未配置)"}`);
  console.log(`- FEISHU_APP_ID：${mask(childEnv.FEISHU_APP_ID)}`);
  console.log("- FEISHU_APP_SECRET：***不显示***");

  const child = spawn(command.command, command.args, {
    cwd: command.cwd,
    env: childEnv,
    stdio: "inherit",
  });
  child.on("exit", (code) => {
    process.exitCode = code ?? 1;
  });
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

function printHelp() {
  console.log(`用法：
  node scripts/run-backend-local.mjs [--env-file backend/.env.feishu.local] [--backend-dir backend] [--port 8080]

说明：
  - 自动读取 backend/.env.feishu.local。
  - 使用 Java 17 启动 Spring Boot 后端。
  - 控制台不会打印 App Secret。`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseRunBackendArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    runBackend(options);
  }
}
