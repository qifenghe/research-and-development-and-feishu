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

test("product-owner pricing detail screens expose the permitted download action", () => {
  const pcSource = readFileSync("apps/pc/src/views/shipment/PricingDetailView.vue", "utf8");
  const mobileSource = readFileSync("apps/mobile/src/views/PricingDetailView.vue", "utf8");

  assert.match(pcSource, /const canDownload = computed\(\(\) =>/);
  assert.match(pcSource, /v-if="canDownload"/);
  assert.match(mobileSource, /const canDownload = computed\(\(\) =>/);
  assert.match(mobileSource, /v-if="canDownload"/);
  assert.match(pcSource, /RND_ENGINEER/);
  assert.match(mobileSource, /RND_ENGINEER/);
});

test("PC finance inbox only queries finance-visible pricing states", () => {
  const source = readFileSync("apps/pc/src/views/shipment/FinanceView.vue", "utf8");
  assert.match(source, /FINANCE_NOTIFIED/);
  assert.match(source, /FINANCE_RECEIVED/);
  assert.doesNotMatch(source, /GENERATED/);
});
