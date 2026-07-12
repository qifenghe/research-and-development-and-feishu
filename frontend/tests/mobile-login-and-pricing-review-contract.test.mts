import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const mobileLogin = readFileSync(new URL("../apps/mobile/src/views/LoginView.vue", import.meta.url), "utf8");
const mobileAuthStore = readFileSync(new URL("../apps/mobile/src/stores/auth.ts", import.meta.url), "utf8");
const sessionApi = readFileSync(new URL("../packages/shared/src/api/session.ts", import.meta.url), "utf8");
const pricingDetail = readFileSync(new URL("../apps/mobile/src/views/PricingDetailView.vue", import.meta.url), "utf8");
const mobileStyles = readFileSync(new URL("../apps/mobile/src/styles/components.css", import.meta.url), "utf8");

test("mobile login submits credentials through the password authentication action", () => {
  assert.match(mobileLogin, /await auth\.loginWithPassword\(form\.username\.trim\(\), form\.password\)/);
  assert.match(mobileAuthStore, /async loginWithPassword\(username: string, password: string\)/);
  assert.match(mobileAuthStore, /api\.session\.login\(username, password\)/);
  assert.match(sessionApi, /client\.post<AuthLoginResult>\("\/auth\/login", \{ username, password \}\)/);
});

test("pending pricing review keeps fixed actions to one row with reserved bottom space", () => {
  const reviewFooter = pricingDetail.match(/<template v-if="canReview">([\s\S]*?)<\/template>/)?.[1] ?? "";

  assert.match(pricingDetail, /'pricing-review-footer': canReview/);
  assert.match(reviewFooter, />\s*审核通过\s*</);
  assert.match(reviewFooter, />\s*更多操作\s*</);
  assert.equal((reviewFooter.match(/<van-button/g) ?? []).length, 2);
  assert.match(mobileStyles, /\.page-with-footer\s*\{[\s\S]*?padding-bottom:\s*128px;/);
  assert.match(
    mobileStyles,
    /\.pricing-review-footer\s+\.form-actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/,
  );
});
