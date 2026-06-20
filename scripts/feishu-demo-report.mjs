import fs from "node:fs";
import process from "node:process";
import { pathToFileURL } from "node:url";

const REQUIRED_IDS = [
  "requestId",
  "taskId",
  "experimentFormId",
  "testAssignmentId",
  "versionId",
  "shipmentId",
  "pricingFileId",
  "financeNotificationId",
];

const REQUIRED_CHECKLIST_EXPECTATIONS = [
  ["sampleNo", (report) => report?.sampleNo],
  ["ids.taskId", (report) => report?.ids?.taskId],
  ["ids.experimentFormId", (report) => report?.ids?.experimentFormId],
  ["ids.shipmentId", (report) => report?.ids?.shipmentId],
  ["ids.pricingFileId", (report) => report?.ids?.pricingFileId],
  ["ids.financeNotificationId", (report) => report?.ids?.financeNotificationId],
];

export function validateDemoReport(report, options = {}) {
  const errors = [];
  const knownRoutes = normalizeKnownRoutes(options.knownRoutes);
  if (report?.ready !== true) {
    errors.push("ready 必须为 true");
  }
  if (!report?.sampleNo) {
    errors.push("sampleNo 缺失");
  }
  for (const idKey of REQUIRED_IDS) {
    if (!report?.ids?.[idKey]) {
      errors.push(`ids.${idKey} 缺失`);
    }
  }
  const checklist = Array.isArray(report?.checklist) ? report.checklist : [];
  if (checklist.length < 6) {
    errors.push("checklist 至少需要 6 项");
  }
  const expectedValues = new Set(checklist.map((item) => item?.expected).filter(Boolean));
  for (const [label, readValue] of REQUIRED_CHECKLIST_EXPECTATIONS) {
    const value = readValue(report);
    if (value && !expectedValues.has(value)) {
      errors.push(`checklist 未覆盖 ${label}：${value}`);
    }
  }
  checklist.forEach((item, index) => {
    let routeCanBeComparedWithUrl = false;
    if (!item.label) {
      errors.push(`checklist[${index}].label 缺失`);
    }
    if (!item.route) {
      errors.push(`checklist[${index}].route 缺失`);
    } else if (!item.route.startsWith("#")) {
      errors.push(`checklist[${index}].route 必须以 # 开头`);
    } else if (knownRoutes && !knownRoutes.has(routeIdFromHash(item.route))) {
      errors.push(`checklist[${index}].route 指向不存在的页面：${item.route}`);
    } else {
      routeCanBeComparedWithUrl = true;
    }
    if (!item.expected) {
      errors.push(`checklist[${index}].expected 缺失`);
    }
    if (item.url) {
      if (!isValidUrl(item.url)) {
        errors.push(`checklist[${index}].url 不是合法 URL`);
      } else if (routeCanBeComparedWithUrl) {
        const urlRoute = routeFromUrl(item.url);
        if (urlRoute && routeIdFromHash(urlRoute) !== routeIdFromHash(item.route)) {
          errors.push(`checklist[${index}].url hash 与 route 不一致：${urlRoute} != ${item.route}`);
        }
      }
    }
  });
  return {
    valid: errors.length === 0,
    errors,
    advice: buildAdvice(errors),
    checklistCount: checklist.length,
  };
}

function buildAdvice(errors) {
  if (errors.length === 0) {
    return "演示报告完整，可以按核对清单进行演示。";
  }
  if (errors.some((error) => error.includes("route 指向不存在的页面"))) {
    return "请确认 --prototype-app 指向最新 prototype-app/app.js，或修正报告中的 route。";
  }
  return "请修正演示报告 JSON，或重新运行 node scripts/feishu-demo-wizard.mjs --json 生成完整报告。";
}

export function loadPrototypeRoutes(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  return [...content.matchAll(/\bid:\s*"([^"]+)"/g)].map((match) => match[1]);
}

function normalizeKnownRoutes(routes) {
  if (!routes) {
    return null;
  }
  return new Set([...routes].map((route) => route.replace(/^#/, "")));
}

function routeIdFromHash(route) {
  return route.slice(1).split(/[?&]/, 1)[0];
}

function routeFromUrl(value) {
  return new URL(value).hash;
}

function isValidUrl(value) {
  try {
    new URL(value);
    return true;
  } catch {
    return false;
  }
}

export function parseReportJson(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`演示报告不是合法 JSON：${error.message}`);
  }
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--file") {
      options.file = argv[++index];
    } else if (arg === "--prototype-app") {
      options.prototypeApp = argv[++index];
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    }
  }
  return options;
}

function readReportText(filePath) {
  if (filePath) {
    return fs.readFileSync(filePath, "utf8");
  }
  return fs.readFileSync(0, "utf8");
}

function printHelp() {
  console.log(`用法：
  node scripts/feishu-demo-wizard.mjs --json > /tmp/feishu-demo-report.json
  node scripts/feishu-demo-report.mjs --file /tmp/feishu-demo-report.json
  node scripts/feishu-demo-report.mjs --file /tmp/feishu-demo-report.json --prototype-app prototype-app/app.js

说明：
  - 校验飞书联调向导 JSON 报告是否包含完整关键 ID 和核对清单。
  - 也可以通过 stdin 传入 JSON。
  - 使用 --prototype-app 时会校验核对项 route 是否存在于原型页面。`);
}

function printResult(result) {
  console.log(`飞书演示报告校验：${result.valid ? "通过" : "未通过"}`);
  console.log(`- 核对项数量：${result.checklistCount}`);
  console.log(`- 下一步：${result.advice}`);
  if (!result.valid) {
    for (const error of result.errors) {
      console.log(`- ${error}`);
    }
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    try {
      const knownRoutes = options.prototypeApp ? loadPrototypeRoutes(options.prototypeApp) : undefined;
      printResult(validateDemoReport(parseReportJson(readReportText(options.file)), { knownRoutes }));
    } catch (error) {
      console.error(`飞书演示报告校验失败：${error.message}`);
      process.exitCode = 1;
    }
  }
}
