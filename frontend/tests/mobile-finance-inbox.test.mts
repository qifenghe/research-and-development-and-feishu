import assert from "node:assert/strict";
import test from "node:test";

import {
  pricingInboxStatusForRole,
  pricingTodoGroupTitle,
} from "../packages/shared/src/api/mobile-todo-policy.ts";

test("finance todo loads notified pricing files instead of unnotified generated files", () => {
  assert.equal(pricingInboxStatusForRole("FINANCE"), "FINANCE_NOTIFIED");
  assert.equal(pricingTodoGroupTitle("FINANCE"), "待接收核价文件");
});

test("assistant todo keeps approved pricing files in the send-to-finance queue", () => {
  assert.equal(pricingInboxStatusForRole("RND_ASSISTANT"), "FINANCE_NOTIFIED");
  assert.equal(pricingTodoGroupTitle("RND_ASSISTANT"), "已移交财务核价");
});
