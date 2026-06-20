import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { loadPrototypeRoutes, validateDemoReport } from "./feishu-demo-report.mjs";

const validReport = {
  ready: true,
  adviceCode: "READY_TO_DEMO",
  sampleNo: "YP202606180001",
  ids: {
    requestId: "REQ-0001",
    taskId: "TASK-0001",
    experimentFormId: "EXP-0001",
    testAssignmentId: "TEST-0001",
    versionId: "VER-0001",
    shipmentId: "SHIP-0001",
    pricingFileId: "PRICE-0001",
    financeNotificationId: "FIN-0001",
  },
  pendingCount: 1,
  dispatchResult: null,
  checklist: [
    {
      label: "工作台查看样品编号",
      route: "#dashboard",
      url: "https://rnd.example.com/app/#dashboard",
      expected: "YP202606180001",
    },
    {
      label: "研发任务查看任务编号",
      route: "#rnd-module",
      url: "https://rnd.example.com/app/#rnd-module",
      expected: "TASK-0001",
    },
    {
      label: "实验单历史查看实验单",
      route: "#experiment-history",
      url: "https://rnd.example.com/app/#experiment-history",
      expected: "EXP-0001",
    },
    {
      label: "寄样核价查看寄样记录",
      route: "#shipment-pricing-module",
      url: "https://rnd.example.com/app/#shipment-pricing-module",
      expected: "SHIP-0001",
    },
    {
      label: "核价文件查看文件记录",
      route: "#pricing-list",
      url: "https://rnd.example.com/app/#pricing-list",
      expected: "PRICE-0001",
    },
    {
      label: "财务通知查看通知记录",
      route: "#pricing-detail",
      url: "https://rnd.example.com/app/#pricing-detail",
      expected: "FIN-0001",
    },
  ],
};

assert.deepEqual(validateDemoReport(validReport), {
  valid: true,
  errors: [],
  checklistCount: 6,
});

assert.deepEqual(validateDemoReport({
  ...validReport,
  ids: {
    ...validReport.ids,
    pricingFileId: "",
  },
  checklist: validReport.checklist.slice(0, 5),
}), {
  valid: false,
  errors: [
    "ids.pricingFileId 缺失",
    "checklist 至少需要 6 项",
  ],
  checklistCount: 5,
});

assert.deepEqual(validateDemoReport({
  ...validReport,
  checklist: [
    {
      ...validReport.checklist[0],
      route: "dashboard",
      url: "not a url",
    },
    ...validReport.checklist.slice(1),
  ],
}), {
  valid: false,
  errors: [
    "checklist[0].route 必须以 # 开头",
    "checklist[0].url 不是合法 URL",
  ],
  checklistCount: 6,
});

assert.deepEqual(validateDemoReport({
  ...validReport,
  checklist: [
    {
      ...validReport.checklist[0],
      route: "#dashboard",
      url: "https://rnd.example.com/app/#pricing-list",
    },
    ...validReport.checklist.slice(1),
  ],
}), {
  valid: false,
  errors: [
    "checklist[0].url hash 与 route 不一致：#pricing-list != #dashboard",
  ],
  checklistCount: 6,
});

assert.deepEqual(validateDemoReport({
  ...validReport,
  checklist: [
    {
      ...validReport.checklist[0],
      route: "#missing-page",
    },
    ...validReport.checklist.slice(1),
  ],
}, { knownRoutes: ["dashboard", "rnd-module", "experiment-history", "shipment-pricing-module", "pricing-list", "pricing-detail"] }), {
  valid: false,
  errors: [
    "checklist[0].route 指向不存在的页面：#missing-page",
  ],
  checklistCount: 6,
});

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "feishu-demo-report-"));
const prototypeAppPath = path.join(tempDir, "app.js");
fs.writeFileSync(prototypeAppPath, `
const views = [
  { id: "dashboard", title: "工作台" },
  { id: "pricing-detail", title: "核价文件详情" },
];
`, "utf8");
assert.deepEqual(loadPrototypeRoutes(prototypeAppPath), ["dashboard", "pricing-detail"]);

console.log("Feishu demo report tests passed.");
