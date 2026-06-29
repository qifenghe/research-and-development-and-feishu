import fs from "node:fs";
import process from "node:process";
import { pathToFileURL } from "node:url";

import { parseEnvText } from "./feishu-local-check.mjs";
import { ApiClient } from "./feishu-demo-task.mjs";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const DEFAULT_DEMO_FILE = ".feishu-demo.local";

const ROLE_BY_KEY = {
  FEISHU_DEMO_ASSISTANT_USER_ID: { role: "RND_ASSISTANT", nameKey: "FEISHU_DEMO_ASSISTANT_NAME", fallbackName: "研发内勤" },
  FEISHU_DEMO_DIRECTOR_USER_ID: { role: "RND_DIRECTOR", nameKey: "FEISHU_DEMO_DIRECTOR_NAME", fallbackName: "研发总监" },
  FEISHU_DEMO_ENGINEER_USER_ID: { role: "RND_ENGINEER", nameKey: "FEISHU_DEMO_ENGINEER_NAME", fallbackName: "研发人员" },
  FEISHU_DEMO_TESTER_USER_ID: { role: "TESTER", nameKey: "FEISHU_DEMO_TESTER_NAME", fallbackName: "李测试", departmentName: "品控部" },
  FEISHU_DEMO_FINANCE_USER_ID: { role: "FINANCE", nameKey: "FEISHU_DEMO_FINANCE_NAME", fallbackName: "钱财务", departmentName: "财务部" },
};

function usersFromEnv(env) {
  const users = [];
  for (const [userIdKey, meta] of Object.entries(ROLE_BY_KEY)) {
    const feishuUserId = env[userIdKey]?.trim();
    if (!feishuUserId || feishuUserId.includes("xxx")) continue;
    users.push({
      feishuUserId,
      role: meta.role,
      name: env[meta.nameKey]?.trim() || meta.fallbackName,
      departmentName: meta.departmentName || "研发部",
    });
  }
  return users;
}

export async function bindUsers(users, backendUrl = DEFAULT_BACKEND_URL) {
  const client = new ApiClient(backendUrl);
  for (const user of users) {
    await client.post("/api/v1/feishu/users/bind", user);
    console.log(`已绑定：${user.name} / ${user.feishuUserId} / ${user.role}`);
  }
}

async function main() {
  const demoFile = process.argv.includes("--demo-file")
    ? process.argv[process.argv.indexOf("--demo-file") + 1]
    : DEFAULT_DEMO_FILE;
  const backendUrl = process.argv.includes("--backend-url")
    ? process.argv[process.argv.indexOf("--backend-url") + 1]
    : DEFAULT_BACKEND_URL;

  if (!fs.existsSync(demoFile)) {
    console.error(`未找到 ${demoFile}。请先复制模板：`);
    console.error("  cp .feishu-demo.local.example .feishu-demo.local");
    console.error("并填入真实的飞书 user_id（ou_ 开头）。");
    process.exitCode = 1;
    return;
  }

  const users = usersFromEnv(parseEnvText(fs.readFileSync(demoFile, "utf8")));
  if (users.length === 0) {
    console.error(`${demoFile} 中没有有效的飞书 user_id，请把 ou_xxx 占位符改成真实 ID。`);
    process.exitCode = 1;
    return;
  }

  await bindUsers(users, backendUrl);
  console.log("\n绑定完成。请从飞书客户端打开应用（不要用 MOCK 登录）。");
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`绑定失败：${error.message}`);
    process.exitCode = 1;
  });
}
