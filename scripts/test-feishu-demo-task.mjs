import assert from "node:assert/strict";

import { runDemoTaskFlow, validateDemoTaskOptions } from "./feishu-demo-task.mjs";

const calls = [];
const client = {
  async post(path, body, options = {}) {
    calls.push(["POST", path, body, options.token || null]);
    if (path === "/api/v1/feishu/oauth/callback") {
      return {
        data: {
          accessToken: body.code.includes("assistant") ? "assistant-token" : "director-token",
        },
      };
    }
    if (path === "/api/v1/sample-requests") {
      assert.equal(options.token, "assistant-token");
      return {
        data: {
          id: "REQ-0001",
          sampleNo: "YP202606180001",
        },
      };
    }
    if (path === "/api/v1/sample-requests/REQ-0001/approve") {
      assert.equal(options.token, "director-token");
      return {
        data: {
          task: {
            id: "TASK-0001",
          },
        },
      };
    }
    if (path === "/api/v1/rnd-tasks/TASK-0001/assign") {
      assert.equal(options.token, "director-token");
      assert.equal(body.assigneeName, "演示研发");
      return {
        data: {
          id: "TASK-0001",
          status: "PENDING_ACCEPTANCE",
        },
      };
    }
    return { data: {} };
  },
  async get(path, options = {}) {
    calls.push(["GET", path, null, options.token || null]);
    assert.equal(path, "/api/v1/feishu/notifications/pending");
    assert.equal(options.token, "director-token");
    return {
      data: [{
        businessId: "TASK-0001",
        recipientFeishuUserId: "ou_demo_engineer",
        templateKey: "RND_TASK_ASSIGNED",
        status: "PENDING_SEND",
      }],
    };
  },
};

const result = await runDemoTaskFlow({
  client,
  assistant: {
    name: "演示内勤",
    feishuUserId: "ou_demo_assistant",
  },
  director: {
    name: "演示总监",
    feishuUserId: "ou_demo_director",
  },
  engineer: {
    name: "演示研发",
    feishuUserId: "ou_demo_engineer",
  },
  productName: "500g香卤大肠头",
  customerName: "LHYC",
});

assert.equal(result.requestId, "REQ-0001");
assert.equal(result.taskId, "TASK-0001");
assert.equal(result.pendingNotifications.length, 1);
assert.deepEqual(calls.map((call) => `${call[0]} ${call[1]}`), [
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/oauth/callback",
  "POST /api/v1/feishu/oauth/callback",
  "POST /api/v1/sample-requests",
  "POST /api/v1/sample-requests/REQ-0001/approve",
  "POST /api/v1/rnd-tasks/TASK-0001/assign",
  "GET /api/v1/feishu/notifications/pending",
]);

assert.throws(
  () => validateDemoTaskOptions({
    dispatch: true,
    engineer: {
      feishuUserId: "ou_demo_engineer",
    },
  }),
  /--dispatch 需要填写真实研发人员飞书 user_id/
);

assert.doesNotThrow(() => validateDemoTaskOptions({
  dispatch: true,
  engineer: {
    feishuUserId: "ou_real_engineer",
  },
}));

console.log("Feishu demo task tests passed.");
