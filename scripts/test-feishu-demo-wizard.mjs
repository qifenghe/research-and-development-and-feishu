import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  buildWizardSummary,
  loadDemoWizardConfig,
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
  sampleNo: "YP202606180001",
  pendingCount: 1,
  dispatchResult: null,
});

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
  productName: "500g香卤大肠头",
  customerName: "LHYC",
});

console.log("Feishu demo wizard tests passed.");
