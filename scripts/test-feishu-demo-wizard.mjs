import assert from "node:assert/strict";

import {
  buildWizardSummary,
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

console.log("Feishu demo wizard tests passed.");
