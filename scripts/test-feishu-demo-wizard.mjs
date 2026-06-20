import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  buildWizardJsonReport,
  buildWizardVerificationChecklist,
  buildWizardSummary,
  loadDemoWizardConfig,
  parseArgs,
  runDemoWizard,
} from "./feishu-demo-wizard.mjs";

let demoCalled = false;
const notReady = await runDemoWizard({
  check: async () => ({
    ready: false,
    envResult: { ready: false, missing: ["FEISHU_APP_ID"] },
  }),
  demo: async () => {
    demoCalled = true;
  },
});

assert.equal(demoCalled, false);
assert.equal(notReady.ready, false);
assert.equal(notReady.advice.code, "FILL_ENV");
assert.equal(notReady.demoResult, null);

const ready = await runDemoWizard({
  check: async () => ({
    ready: true,
    envResult: { ready: true, missing: [] },
    statusResult: { ready: true, missing: [] },
  }),
  demo: async (options) => ({
    requestId: "REQ-0001",
    taskId: "TASK-0001",
    experimentFormId: "EXP-0001",
    testAssignmentId: "TEST-0001",
    versionId: "VER-0001",
    shipmentId: "SHIP-0001",
    pricingFileId: "PRICE-0001",
    financeNotificationId: "FIN-0001",
    sampleNo: "YP202606180001",
    engineer: {
      name: options.engineer.name,
      feishuUserId: options.engineer.feishuUserId,
    },
    pendingNotifications: [{ businessId: "TASK-0001", status: "PENDING_SEND" }],
    dispatchResult: null,
  }),
  engineer: {
    name: "张研发",
    feishuUserId: "ou_real_engineer",
  },
});

assert.equal(ready.ready, true);
assert.equal(ready.advice.code, "READY_TO_DEMO");
assert.equal(ready.demoResult.taskId, "TASK-0001");
assert.equal(ready.demoResult.engineer.name, "张研发");

assert.deepEqual(buildWizardSummary(ready), {
  ready: true,
  adviceCode: "READY_TO_DEMO",
  requestId: "REQ-0001",
  taskId: "TASK-0001",
  experimentFormId: "EXP-0001",
  testAssignmentId: "TEST-0001",
  versionId: "VER-0001",
  shipmentId: "SHIP-0001",
  pricingFileId: "PRICE-0001",
  financeNotificationId: "FIN-0001",
  sampleNo: "YP202606180001",
  pendingCount: 1,
  dispatchResult: null,
});

assert.deepEqual(buildWizardVerificationChecklist(buildWizardSummary(ready)), [
  {
    label: "工作台查看样品编号",
    route: "#dashboard",
    expected: "YP202606180001",
  },
  {
    label: "研发任务查看任务编号",
    route: "#rnd-module",
    expected: "TASK-0001",
  },
  {
    label: "实验单历史查看实验单",
    route: "#experiment-history",
    expected: "EXP-0001",
  },
  {
    label: "寄样核价查看寄样记录",
    route: "#shipment-pricing-module",
    expected: "SHIP-0001",
  },
  {
    label: "核价文件查看文件记录",
    route: "#pricing-list",
    expected: "PRICE-0001",
  },
  {
    label: "财务通知查看通知记录",
    route: "#pricing-detail",
    expected: "FIN-0001",
  },
]);

assert.deepEqual(
  buildWizardVerificationChecklist(buildWizardSummary(ready), "https://rnd.example.com/app/").map((item) => item.url),
  [
    "https://rnd.example.com/app/#dashboard",
    "https://rnd.example.com/app/#rnd-module",
    "https://rnd.example.com/app/#experiment-history",
    "https://rnd.example.com/app/#shipment-pricing-module",
    "https://rnd.example.com/app/#pricing-list",
    "https://rnd.example.com/app/#pricing-detail",
  ],
);

assert.deepEqual(buildWizardJsonReport(ready, "https://rnd.example.com/app/"), {
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
  checklist: buildWizardVerificationChecklist(buildWizardSummary(ready), "https://rnd.example.com/app/"),
});

assert.equal(parseArgs(["--json"]).json, true);

let receivedPeople = null;
await runDemoWizard({
  check: async () => ({
    ready: true,
    envResult: { ready: true, missing: [] },
    statusResult: { ready: true, missing: [] },
  }),
  demo: async (options) => {
    receivedPeople = {
      assistant: options.assistant,
      director: options.director,
      engineer: options.engineer,
    };
    return {
      requestId: "REQ-0002",
      taskId: "TASK-0002",
      sampleNo: "YP202606180002",
      engineer: options.engineer,
      pendingNotifications: [],
      dispatchResult: null,
    };
  },
  assistant: {
    name: "李内勤",
    feishuUserId: "ou_assistant_real",
  },
  director: {
    name: "王总监",
    feishuUserId: "ou_director_real",
  },
  engineer: {
    name: "张研发",
    feishuUserId: "ou_engineer_real",
  },
});

assert.deepEqual(receivedPeople, {
  assistant: {
    name: "李内勤",
    feishuUserId: "ou_assistant_real",
  },
  director: {
    name: "王总监",
    feishuUserId: "ou_director_real",
  },
  engineer: {
    name: "张研发",
    feishuUserId: "ou_engineer_real",
  },
});

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "feishu-demo-wizard-"));
const demoConfigFile = path.join(tempDir, ".feishu-demo.local");
fs.writeFileSync(demoConfigFile, `
FEISHU_DEMO_ASSISTANT_NAME=赵内勤
FEISHU_DEMO_ASSISTANT_USER_ID=ou_assistant_file
FEISHU_DEMO_DIRECTOR_NAME=钱总监
FEISHU_DEMO_DIRECTOR_USER_ID=ou_director_file
FEISHU_DEMO_ENGINEER_NAME=孙研发
FEISHU_DEMO_ENGINEER_USER_ID=ou_engineer_file
FEISHU_DEMO_TESTER_NAME=周测试
FEISHU_DEMO_FINANCE_RECIPIENT_NAME=吴财务
FEISHU_DEMO_PRODUCT_NAME=500g香卤大肠头
FEISHU_DEMO_CUSTOMER_NAME=LHYC
`);

assert.deepEqual(loadDemoWizardConfig(demoConfigFile), {
  assistant: {
    name: "赵内勤",
    feishuUserId: "ou_assistant_file",
  },
  director: {
    name: "钱总监",
    feishuUserId: "ou_director_file",
  },
  engineer: {
    name: "孙研发",
    feishuUserId: "ou_engineer_file",
  },
  testerName: "周测试",
  financeRecipientName: "吴财务",
  productName: "500g香卤大肠头",
  customerName: "LHYC",
});

let filePeople = null;
await runDemoWizard({
  demoConfigFile,
  check: async () => ({
    ready: true,
    envResult: { ready: true, missing: [] },
    statusResult: { ready: true, missing: [] },
  }),
  demo: async (options) => {
    filePeople = {
      assistant: options.assistant,
      director: options.director,
      engineer: options.engineer,
      testerName: options.testerName,
      financeRecipientName: options.financeRecipientName,
      productName: options.productName,
      customerName: options.customerName,
    };
    return {
      requestId: "REQ-0003",
      taskId: "TASK-0003",
      sampleNo: "YP202606180003",
      engineer: options.engineer,
      pendingNotifications: [],
      dispatchResult: null,
    };
  },
});

assert.deepEqual(filePeople, {
  assistant: {
    name: "赵内勤",
    feishuUserId: "ou_assistant_file",
  },
  director: {
    name: "钱总监",
    feishuUserId: "ou_director_file",
  },
  engineer: {
    name: "孙研发",
    feishuUserId: "ou_engineer_file",
  },
  testerName: "周测试",
  financeRecipientName: "吴财务",
  productName: "500g香卤大肠头",
  customerName: "LHYC",
});

console.log("Feishu demo wizard tests passed.");
