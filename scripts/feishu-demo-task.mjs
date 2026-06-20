import http from "node:http";
import https from "node:https";
import process from "node:process";
import { pathToFileURL } from "node:url";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

const DEFAULT_OPTIONS = {
  productName: "500g香卤大肠头",
  productType: "冷冻即热菜",
  customerName: "LHYC",
  specification: "500g/袋",
  dueDate: "2026-06-25",
  assistant: {
    name: "演示内勤",
    feishuUserId: "ou_demo_assistant",
    role: "RND_ASSISTANT",
    departmentName: "研发部",
  },
  director: {
    name: "演示总监",
    feishuUserId: "ou_demo_director",
    role: "RND_DIRECTOR",
    departmentName: "研发部",
  },
  engineer: {
    name: "演示研发",
    feishuUserId: "ou_demo_engineer",
    role: "RND_ENGINEER",
    departmentName: "研发部",
  },
};

export async function runDemoTaskFlow(options = {}) {
  const client = options.client || new ApiClient(options.backendUrl || DEFAULT_BACKEND_URL);
  const assistant = personWithDefaults(options.assistant, DEFAULT_OPTIONS.assistant);
  const director = personWithDefaults(options.director, DEFAULT_OPTIONS.director);
  const engineer = personWithDefaults(options.engineer, DEFAULT_OPTIONS.engineer);

  await bindUser(client, assistant);
  await bindUser(client, director);
  await bindUser(client, engineer);

  const assistantToken = await login(client, assistant.feishuUserId);
  const directorToken = await login(client, director.feishuUserId);

  const sampleRequest = await client.post("/api/v1/sample-requests", {
    productName: options.productName || DEFAULT_OPTIONS.productName,
    productType: options.productType || DEFAULT_OPTIONS.productType,
    customerName: options.customerName || DEFAULT_OPTIONS.customerName,
    specification: options.specification || DEFAULT_OPTIONS.specification,
    creatorName: assistant.name,
  }, { token: assistantToken });

  const requestId = sampleRequest.data.id;
  const approved = await client.post(`/api/v1/sample-requests/${requestId}/approve`, {
    reviewerName: director.name,
  }, { token: directorToken });

  const taskId = approved.data.task.id;
  await client.post(`/api/v1/rnd-tasks/${taskId}/assign`, {
    assigneeName: engineer.name,
    dueDate: options.dueDate || DEFAULT_OPTIONS.dueDate,
  }, { token: directorToken });

  const pending = await client.get("/api/v1/feishu/notifications/pending", { token: directorToken });
  let dispatchResult = null;
  if (options.dispatch === true) {
    dispatchResult = await client.post("/api/v1/feishu/notifications/dispatch", {}, { token: directorToken });
  }

  return {
    requestId,
    taskId,
    sampleNo: sampleRequest.data.sampleNo,
    engineer,
    pendingNotifications: pending.data,
    dispatchResult: dispatchResult?.data || null,
  };
}

class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
  }

  async post(path, body, options = {}) {
    return this.request("POST", path, body, options);
  }

  async get(path, options = {}) {
    return this.request("GET", path, null, options);
  }

  async request(method, apiPath, body, options = {}) {
    const url = new URL(apiPath, `${this.baseUrl}/`);
    const headers = {
      Accept: "application/json",
    };
    let requestBody = null;
    if (body !== null && body !== undefined) {
      requestBody = JSON.stringify(body);
      headers["Content-Type"] = "application/json";
      headers["Content-Length"] = Buffer.byteLength(requestBody);
    }
    if (options.token) {
      headers.Authorization = `Bearer ${options.token}`;
    }

    const responseBody = await requestText(url, method, headers, requestBody);
    let payload;
    try {
      payload = JSON.parse(responseBody);
    } catch (error) {
      throw new Error(`${method} ${apiPath} 响应不是 JSON：${error.message}`);
    }
    if (payload.code !== "0") {
      throw new Error(`${method} ${apiPath} 失败：${payload.code || "UNKNOWN"} ${payload.message || ""}`.trim());
    }
    return payload;
  }
}

function personWithDefaults(person = {}, defaults) {
  return {
    ...defaults,
    ...person,
    role: person.role || defaults.role,
    departmentName: person.departmentName || defaults.departmentName,
  };
}

async function bindUser(client, person) {
  await client.post("/api/v1/feishu/users/bind", {
    name: person.name,
    feishuUserId: person.feishuUserId,
    role: person.role,
    departmentName: person.departmentName,
  });
}

async function login(client, feishuUserId) {
  const response = await client.post("/api/v1/feishu/oauth/callback", {
    code: `mock:${feishuUserId}`,
  });
  return response.data.accessToken;
}

function requestText(url, method, headers, body) {
  const transport = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const request = transport.request(url, { method, headers, timeout: 10000 }, (response) => {
      let responseBody = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        responseBody += chunk;
      });
      response.on("end", () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`HTTP ${response.statusCode}: ${responseBody.slice(0, 300)}`));
          return;
        }
        resolve(responseBody);
      });
    });
    request.on("timeout", () => {
      request.destroy(new Error(`请求超时：${url.href}`));
    });
    request.on("error", reject);
    if (body) {
      request.write(body);
    }
    request.end();
  });
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--backend-url") {
      options.backendUrl = argv[++index];
    } else if (arg === "--engineer-name") {
      options.engineer = { ...(options.engineer || {}), name: argv[++index] };
    } else if (arg === "--engineer-feishu-user-id") {
      options.engineer = { ...(options.engineer || {}), feishuUserId: argv[++index] };
    } else if (arg === "--product-name") {
      options.productName = argv[++index];
    } else if (arg === "--customer-name") {
      options.customerName = argv[++index];
    } else if (arg === "--dispatch") {
      options.dispatch = true;
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    }
  }
  return options;
}

function printHelp() {
  console.log(`用法：
  node scripts/feishu-demo-task.mjs \\
    --backend-url http://127.0.0.1:8080 \\
    --engineer-name 张研发 \\
    --engineer-feishu-user-id ou_xxx

说明：
  - 默认只生成一条 PENDING_SEND 飞书任务通知，不会真实派发。
  - 加 --dispatch 后会调用 /api/v1/feishu/notifications/dispatch，真实 OPENAPI 模式下会尝试发送飞书消息。
  - 需要后端已启动；如果开启鉴权，脚本会自动绑定演示用户并通过 mock 免登拿 token。`);
}

function printResult(result, dispatch) {
  console.log("飞书研发任务演示数据已生成");
  console.log(`- 样品需求：${result.requestId}`);
  console.log(`- 样品编号：${result.sampleNo}`);
  console.log(`- 研发任务：${result.taskId}`);
  console.log(`- 接收研发：${result.engineer.name} / ${result.engineer.feishuUserId}`);
  console.log(`- 待发送通知数：${result.pendingNotifications.length}`);
  const matched = result.pendingNotifications.find((item) => item.businessId === result.taskId);
  if (matched) {
    console.log(`- 当前通知状态：${matched.status}`);
    console.log(`- 模板：${matched.templateKey}`);
  }
  if (dispatch) {
    console.log(`- 派发结果：attempted=${result.dispatchResult.attemptedCount}, sent=${result.dispatchResult.sentCount}, failed=${result.dispatchResult.failedCount}`);
  } else {
    console.log("- 尚未派发。如确认要发飞书消息，再运行同命令并增加 --dispatch。");
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    runDemoTaskFlow(options)
      .then((result) => printResult(result, options.dispatch === true))
      .catch((error) => {
        console.error(`飞书研发任务演示数据生成失败：${error.message}`);
        process.exitCode = 1;
      });
  }
}
