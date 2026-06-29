import http from "node:http";
import process from "node:process";
import { pathToFileURL } from "node:url";

const DEFAULT_PORT = 8787;
const DEFAULT_BACKEND = "http://127.0.0.1:8080";
const DEFAULT_PC = "http://localhost:5173";
const DEFAULT_MOBILE = "http://localhost:5174";
const DEFAULT_HOST = "0.0.0.0";

export function parseGatewayArgs(argv) {
  const options = {
    port: DEFAULT_PORT,
    backend: DEFAULT_BACKEND,
    pc: DEFAULT_PC,
    mobile: DEFAULT_MOBILE,
    host: DEFAULT_HOST,
  };
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--port") options.port = Number(argv[++index]);
    else if (arg === "--backend") options.backend = argv[++index];
    else if (arg === "--pc") options.pc = argv[++index];
    else if (arg === "--mobile") options.mobile = argv[++index];
    else if (arg === "--host") options.host = argv[++index];
    else if (arg === "--help" || arg === "-h") options.help = true;
  }
  return options;
}

function stripTrailingSlash(value) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}

function proxyTarget(options, pathname) {
  if (pathname === "/api" || pathname.startsWith("/api/")) {
    return { target: stripTrailingSlash(options.backend), path: pathname };
  }
  if (pathname === "/admin" || pathname.startsWith("/admin/")) {
    return {
      target: stripTrailingSlash(options.pc),
      path: pathname === "/admin" ? "/admin/" : pathname,
    };
  }
  if (pathname === "/m" || pathname.startsWith("/m/")) {
    return {
      target: stripTrailingSlash(options.mobile),
      path: pathname === "/m" ? "/m/" : pathname,
    };
  }
  return null;
}

function proxyRequest(req, res, targetUrl) {
  const headers = { ...req.headers, host: targetUrl.host };
  delete headers.connection;
  // The browser calls the gateway as same-origin, but the gateway forwards to
  // localhost services. Removing Origin keeps backend CORS from treating this
  // dev proxy hop as a cross-origin browser request.
  delete headers.origin;
  const proxyReq = http.request(
    targetUrl,
    { method: req.method, headers },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers);
      proxyRes.pipe(res);
    },
  );
  proxyReq.on("error", (error) => {
    if (!res.headersSent) {
      res.writeHead(502, { "Content-Type": "text/plain; charset=utf-8" });
    }
    res.end(`Gateway proxy error: ${error.message}`);
  });
  if (req.method === "GET" || req.method === "HEAD") {
    proxyReq.end();
    return;
  }
  req.pipe(proxyReq);
}

export function createGatewayServer(options) {
  return http.createServer((req, res) => {
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "127.0.0.1"}`);

    if (url.pathname === "/" || url.pathname === "") {
      res.writeHead(302, { Location: "/admin/dashboard" });
      res.end();
      return;
    }

    const route = proxyTarget(options, url.pathname);
    if (!route) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Not Found");
      return;
    }

    const target = new URL(route.path + url.search, `${route.target}/`);
    proxyRequest(req, res, target);
  });
}

export function startGateway(options = {}) {
  const resolved = { ...parseGatewayArgs([]), ...options };
  const server = createGatewayServer(resolved);
  server.listen(resolved.port, resolved.host, () => {
    const displayHost = resolved.host === "0.0.0.0" ? "本机局域网IP" : resolved.host;
    console.log("研发样品管理统一网关已启动");
    console.log(`- 监听地址：${resolved.host}:${resolved.port}`);
    console.log(`- 网关地址：http://${displayHost}:${resolved.port}`);
    console.log(`- PC 入口：http://${displayHost}:${resolved.port}/admin/dashboard`);
    console.log(`- 手机入口：http://${displayHost}:${resolved.port}/m/todo`);
    console.log(`- API 代理：http://${displayHost}:${resolved.port}/api/v1/...`);
  });
  return server;
}

function printHelp() {
  console.log(`用法：
  node scripts/dev-gateway.mjs [--port 8787] [--host 0.0.0.0] [--backend http://127.0.0.1:8080] [--pc http://127.0.0.1:5173] [--mobile http://127.0.0.1:5174]

手机联调请使用 --host 0.0.0.0，然后访问 http://<电脑局域网IP>:8787/m/todo`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseGatewayArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    startGateway(options);
  }
}
