import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { canAccessRoute } from "../packages/shared/src/permissions/index.ts";

test("product owners can reach pricing lists and details on PC and mobile", () => {
  assert.equal(canAccessRoute("RND_ENGINEER", "/pricing/list", "pc"), true);
  assert.equal(canAccessRoute("RND_ENGINEER", "/pricing/PRICE-001", "pc"), true);
  assert.equal(canAccessRoute("RND_ENGINEER", "/pricing", "mobile"), true);
  assert.equal(canAccessRoute("RND_ENGINEER", "/pricing/PRICE-001", "mobile"), true);
});

test("PC finance inbox only queries finance-visible pricing states", () => {
  const source = readFileSync("apps/pc/src/views/shipment/FinanceView.vue", "utf8");
  assert.match(source, /FINANCE_NOTIFIED/);
  assert.match(source, /FINANCE_RECEIVED/);
  assert.doesNotMatch(source, /GENERATED/);
});
