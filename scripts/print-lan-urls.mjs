import os from "node:os";

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

function printUrls(label, port, paths) {
  const localhost = `http://127.0.0.1:${port}`;
  const lanAddresses = listLanAddresses();
  console.log(`\n${label}`);
  console.log(`- 本机：${localhost}${paths.local}`);
  for (const ip of lanAddresses) {
    console.log(`- 局域网：http://${ip}:${port}${paths.lan}`);
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
  local: "/m/todo",
  lan: "/m/todo",
});
console.log(`\n后端 API：http://127.0.0.1:${PORTS.backend}/api/v1/...`);
console.log("MOCK 登录前请先在电脑执行用户 bind。");
