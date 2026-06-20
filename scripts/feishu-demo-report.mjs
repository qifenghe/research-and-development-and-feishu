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

export function validateDemoReport(report) {
  const errors = [];
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
  checklist.forEach((item, index) => {
    if (!item.label) {
      errors.push(`checklist[${index}].label 缺失`);
    }
    if (!item.route) {
      errors.push(`checklist[${index}].route 缺失`);
    } else if (!item.route.startsWith("#")) {
      errors.push(`checklist[${index}].route 必须以 # 开头`);
    }
    if (!item.expected) {
      errors.push(`checklist[${index}].expected 缺失`);
    }
    if (item.url && !isValidUrl(item.url)) {
      errors.push(`checklist[${index}].url 不是合法 URL`);
    }
  });
  return {
    valid: errors.length === 0,
    errors,
    checklistCount: checklist.length,
  };
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

说明：
  - 校验飞书联调向导 JSON 报告是否包含完整关键 ID 和核对清单。
  - 也可以通过 stdin 传入 JSON。`);
}

function printResult(result) {
  console.log(`飞书演示报告校验：${result.valid ? "通过" : "未通过"}`);
  console.log(`- 核对项数量：${result.checklistCount}`);
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
      printResult(validateDemoReport(parseReportJson(readReportText(options.file))));
    } catch (error) {
      console.error(`飞书演示报告校验失败：${error.message}`);
      process.exitCode = 1;
    }
  }
}
