import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { canAccessRoute } from "../packages/shared/src/permissions/index.ts";

const mobileRouter = readFileSync(new URL("../apps/mobile/src/router/index.ts", import.meta.url), "utf8");
const pricingList = readFileSync(new URL("../apps/mobile/src/views/PricingListView.vue", import.meta.url), "utf8");

test("mobile pricing card links resolve through registered list and detail routes", () => {
  assert.match(pricingList, /:to="`\/pricing\/\$\{file\.id\}`"/);
  assert.match(
    mobileRouter,
    /path: "pricing", name: "pricing-list", component: \(\) => import\("\.\.\/views\/PricingListView\.vue"\)/,
  );
  assert.match(
    mobileRouter,
    /path: "pricing\/:id", name: "pricing-detail", component: \(\) => import\("\.\.\/views\/PricingDetailView\.vue"\)/,
  );
});

test("Task 4 pricing roles can access mobile list and details", () => {
  for (const role of ["RND_DIRECTOR", "RND_ENGINEER", "FINANCE"]) {
    assert.equal(canAccessRoute(role, "/pricing", "mobile"), true, `${role} should access the pricing list`);
    assert.equal(canAccessRoute(role, "/pricing/PRICE-001", "mobile"), true, `${role} should access a pricing detail`);
  }
});

test("unrelated mobile roles cannot access pricing routes", () => {
  for (const role of ["TESTER", "QA_TESTER"]) {
    assert.equal(canAccessRoute(role, "/pricing", "mobile"), false, `${role} should not access the pricing list`);
    assert.equal(canAccessRoute(role, "/pricing/PRICE-001", "mobile"), false, `${role} should not access a pricing detail`);
  }
});
