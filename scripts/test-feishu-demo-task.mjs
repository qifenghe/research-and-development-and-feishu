import assert from "node:assert/strict";

import { runDemoTaskFlow, validateDemoTaskOptions } from "./feishu-demo-task.mjs";

const calls = [];
const client = {
  async post(path, body, options = {}) {
    calls.push(["POST", path, body, options.token || null]);
    if (path === "/api/v1/feishu/oauth/callback") {
      let accessToken = "director-token";
      if (body.code.includes("assistant")) {
        accessToken = "assistant-token";
      } else if (body.code.includes("engineer")) {
        accessToken = "engineer-token";
      }
      return {
        data: {
          accessToken,
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
    if (path === "/api/v1/rnd-tasks/TASK-0001/accept") {
      assert.equal(options.token, "engineer-token");
      assert.equal(body.acceptedBy, "演示研发");
      return {
        data: {
          id: "TASK-0001",
          status: "SAMPLING",
        },
      };
    }
    if (path === "/api/v1/rnd-tasks/TASK-0001/experiment-form/draft") {
      assert.equal(options.token, "engineer-token");
      assert.equal(body.operatorName, "演示研发");
      assert.ok(body.materials.length > 0);
      return {
        data: {
          id: "EXP-0001",
          versionId: "VER-0001",
          status: "DRAFT",
        },
      };
    }
    if (path === "/api/v1/experiment-forms/EXP-0001/submit-test") {
      assert.equal(options.token, "engineer-token");
      assert.equal(body.testerName, "内部测试员");
      return {
        data: {
          experimentForm: {
            id: "EXP-0001",
            status: "SUBMITTED_FOR_TEST",
          },
          testAssignment: {
            id: "TEST-0001",
            status: "PENDING_TEST",
          },
        },
      };
    }
    if (path === "/api/v1/test-assignments/TEST-0001/pass") {
      assert.equal(options.token, "engineer-token");
      assert.equal(body.testerName, "内部测试员");
      return {
        data: {
          experimentForm: {
            id: "EXP-0001",
            versionId: "VER-0001",
            status: "LOCKED",
          },
          testAssignment: {
            id: "TEST-0001",
            status: "PASSED",
          },
          testRecord: {
            id: "TREC-0001",
          },
        },
      };
    }
    if (path === "/api/v1/sample-versions/VER-0001/shipments") {
      assert.equal(options.token, "director-token");
      assert.equal(body.quantity, 6);
      return {
        data: {
          id: "SHIP-0001",
          versionId: "VER-0001",
          status: "SHIPPED",
        },
      };
    }
    if (path === "/api/v1/shipments/SHIP-0001/feedback") {
      assert.equal(options.token, "director-token");
      assert.equal(body.result, "PASSED");
      return {
        data: {
          shipment: {
            id: "SHIP-0001",
            status: "FEEDBACK_PASSED",
          },
          feedback: {
            id: "CFB-0001",
            result: "PASSED",
          },
        },
      };
    }
    if (path === "/api/v1/sample-versions/VER-0001/pricing-files") {
      assert.equal(options.token, "director-token");
      return {
        data: {
          id: "PRICE-0001",
          versionId: "VER-0001",
          pricingVersion: "A0-核价V1",
          status: "GENERATED",
        },
      };
    }
    if (path === "/api/v1/pricing-files/PRICE-0001/notify-finance") {
      assert.equal(options.token, "director-token");
      assert.equal(body.recipientName, "财务核价员");
      return {
        data: {
          pricingFile: {
            id: "PRICE-0001",
            status: "FINANCE_NOTIFIED",
          },
          notification: {
            id: "FIN-0001",
            status: "SENT",
          },
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
assert.equal(result.experimentFormId, "EXP-0001");
assert.equal(result.testAssignmentId, "TEST-0001");
assert.equal(result.versionId, "VER-0001");
assert.equal(result.shipmentId, "SHIP-0001");
assert.equal(result.customerFeedbackId, "CFB-0001");
assert.equal(result.pricingFileId, "PRICE-0001");
assert.equal(result.financeNotificationId, "FIN-0001");
assert.equal(result.pendingNotifications.length, 1);
assert.deepEqual(calls.map((call) => `${call[0]} ${call[1]}`), [
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/users/bind",
  "POST /api/v1/feishu/oauth/callback",
  "POST /api/v1/feishu/oauth/callback",
  "POST /api/v1/feishu/oauth/callback",
  "POST /api/v1/sample-requests",
  "POST /api/v1/sample-requests/REQ-0001/approve",
  "POST /api/v1/rnd-tasks/TASK-0001/assign",
  "POST /api/v1/rnd-tasks/TASK-0001/accept",
  "POST /api/v1/rnd-tasks/TASK-0001/experiment-form/draft",
  "POST /api/v1/experiment-forms/EXP-0001/submit-test",
  "POST /api/v1/test-assignments/TEST-0001/pass",
  "POST /api/v1/sample-versions/VER-0001/shipments",
  "POST /api/v1/shipments/SHIP-0001/feedback",
  "POST /api/v1/sample-versions/VER-0001/pricing-files",
  "POST /api/v1/pricing-files/PRICE-0001/notify-finance",
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
