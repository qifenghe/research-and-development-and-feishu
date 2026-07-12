import { spawn } from "node:child_process";

const checks = [
  ["后端", "http://127.0.0.1:8080/actuator/health"],
  ["PC 端", "http://127.0.0.1:5173/admin/"],
  ["手机端", "http://127.0.0.1:5174/m/"],
];

for (const [name, url] of checks) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(3_000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    process.stdout.write(`[OK] ${name} ${url}\n`);
  } catch (error) {
    process.stderr.write(`[FAIL] ${name}服务不可用：${error instanceof Error ? error.message : error}\n`);
    process.exit(1);
  }
}

const child = spawn(
  "pnpm",
  [
    "--dir",
    "frontend",
    "exec",
    "playwright",
    "test",
    "e2e/mobile-closed-loop.spec.ts",
    "--project=mobile-chromium",
    "--workers=1",
  ],
  { cwd: new URL("..", import.meta.url), stdio: "inherit" },
);

child.on("exit", (code) => process.exit(code ?? 1));
