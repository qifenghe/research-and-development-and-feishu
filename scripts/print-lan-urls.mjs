import os from "node:os";
import { execFileSync } from "node:child_process";

const PORTS = {
  gateway: 8787,
  pc: 5173,
  mobile: 5174,
  backend: 8080,
};

function listLanAddresses() {
  try {
    const addresses = new Set();
    for (const interfaces of Object.values(os.networkInterfaces())) {
      for (const item of interfaces ?? []) {
        if (item.family !== "IPv4" || item.internal) continue;
        addresses.add(item.address);
      }
    }
    return [...addresses];
  } catch {
    return [];
  }
}

function localHostname() {
  try {
    return execFileSync("scutil", ["--get", "LocalHostName"], { encoding: "utf8" }).trim().replace(/\.local$/i, "");
  } catch {
    // Fall back for environments without macOS's LocalHostName service.
  }
  return os.hostname().replace(/\.local$/i, "");
}

function printUrls(label, port, paths) {
  const localhost = `http://127.0.0.1:${port}`;
  const hostname = localHostname();
  const lanAddresses = listLanAddresses();
  console.log(`\n${label}`);
  console.log(`- 推荐：http://${hostname}.local:${port}${paths.local}`);
  console.log(`- 本机：${localhost}${paths.local}`);
  for (const ip of lanAddresses) {
    console.log(`- IPv4 备用：http://${ip}:${port}${paths.lan}`);
  }
  if (lanAddresses.length === 0) {
    console.log("- 局域网：请在本机终端运行 node scripts/print-lan-urls.mjs 查看 IP");
  }
}

console.log("研发样品管理本地访问地址");
printUrls("统一网关（推荐手机联调）", PORTS.gateway, {
  local: "/admin/dashboard",
  lan: "/m/todo",
});
printUrls("PC 前端直连", PORTS.pc, {
  local: "/admin/dashboard",
  lan: "/admin/dashboard",
});
printUrls("手机前端直连", PORTS.mobile, {
  local: "/m/login",
  lan: "/m/login",
});
console.log(`\n后端 API：http://127.0.0.1:${PORTS.backend}/api/v1/...`);
console.log("H5 默认登录账号：rnd_assistant / 123456。");
