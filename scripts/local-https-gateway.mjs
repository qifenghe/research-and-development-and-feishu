import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import process from "node:process";
import { pathToFileURL } from "node:url";

const DEFAULT_HTTPS_PORT = 8443;
const DEFAULT_HTTP_PORT = 8088;
const DEFAULT_TARGET = "http://127.0.0.1:8790";
const DEFAULT_CERT = "/Users/lhrzp/Downloads/25733930_rnd/rnd.longhuishiping.com.pem";
const DEFAULT_KEY = "/Users/lhrzp/Downloads/25733930_rnd/rnd.longhuishiping.com.key";
const DEFAULT_DOMAIN = "rnd.longhuishiping.com";

function parseArgs(argv) {
  const options = {
    httpsPort: DEFAULT_HTTPS_PORT,
    httpPort: DEFAULT_HTTP_PORT,
    target: DEFAULT_TARGET,
    cert: DEFAULT_CERT,
    key: DEFAULT_KEY,
    domain: DEFAULT_DOMAIN,
  };
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--https-port") options.httpsPort = Number(argv[++index]);
    else if (arg === "--http-port") options.httpPort = Number(argv[++index]);
    else if (arg === "--target") options.target = argv[++index];
    else if (arg === "--cert") options.cert = argv[++index];
    else if (arg === "--key") options.key = argv[++index];
    else if (arg === "--domain") options.domain = argv[++index];
    else if (arg === "--help" || arg === "-h") options.help = true;
  }
  return options;
}

function proxyRequest(req, res, targetRoot) {
  const target = new URL(req.url ?? "/", targetRoot);
  const headers = { ...req.headers, host: target.host };
  delete headers.connection;
  const proxy = http.request(
    target,
    { method: req.method, headers },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers);
      proxyRes.pipe(res);
    },
  );
  proxy.on("error", (error) => {
    if (!res.headersSent) {
      res.writeHead(502, { "Content-Type": "text/plain; charset=utf-8" });
    }
    res.end(`Local HTTPS gateway proxy error: ${error.message}`);
  });
  if (req.method === "GET" || req.method === "HEAD") {
    proxy.end();
    return;
  }
  req.pipe(proxy);
}

function start(options) {
  const target = new URL(options.target);
  const tls = {
    cert: fs.readFileSync(options.cert),
    key: fs.readFileSync(options.key),
  };

  const httpsServer = https.createServer(tls, (req, res) => proxyRequest(req, res, target));
  httpsServer.listen(options.httpsPort, "0.0.0.0", () => {
    console.log("本机 HTTPS 网关已启动");
    console.log(`- HTTPS 本机端口：https://0.0.0.0:${options.httpsPort}`);
    console.log(`- 转发目标：${target.href}`);
    console.log(`- 正式域名：https://${options.domain}`);
  });

  const httpServer = http.createServer((req, res) => {
    const location = `https://${options.domain}${req.url ?? "/"}`;
    res.writeHead(301, { Location: location });
    res.end();
  });
  httpServer.listen(options.httpPort, "0.0.0.0", () => {
    console.log(`- HTTP 跳转端口：http://0.0.0.0:${options.httpPort} -> https://${options.domain}`);
  });

  return { httpServer, httpsServer };
}

function printHelp() {
  console.log(`用法：
  node scripts/local-https-gateway.mjs [--https-port 8443] [--http-port 8088] [--target http://127.0.0.1:8790]

说明：
  - 使用 rnd.longhuishiping.com 证书在本机启动 HTTPS。
  - 推荐路由器端口映射：
      外网 443 -> 本机 8443
      外网 80  -> 本机 8088
  - 飞书后台仍填写：
      https://rnd.longhuishiping.com/m/login
      https://rnd.longhuishiping.com/admin/dashboard`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    start(options);
  }
}
