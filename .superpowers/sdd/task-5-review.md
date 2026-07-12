# Task 5 Independent Review

**Range:** `5b1ad85..a0a8e87`  
**Reviewed:** 2026-07-12  
**Decision:** Changes requested. Not approved.

## Findings

### Important

1. **Password login cannot compile or reach the mobile workspace.**  
   `LoginView` calls `auth.loginWithPassword(...)`, but the reviewed `auth` store exposes only `loginWithFeishuCode` and `mockLogin`; it has no `loginWithPassword` action. `pnpm typecheck` fails with TS2339 at `LoginView.vue:88`. Vite can still render the login screen, but pressing Login throws a `TypeError`, which the new catch block mislabels as a network error, so users remain on the login page. This blocks every requested role flow: todo, request entry/review, task/experiment, test, pricing review, and finance inbox.  
   References: `frontend/apps/mobile/src/views/LoginView.vue:88-104`; `frontend/apps/mobile/src/stores/auth.ts:27-67`.

2. **The pending-pricing review footer can cover content on small screens.**  
   A director reviewing a pending pricing file renders Download, Approve, and Reject in one fixed footer. The page reserves only `128px` at the bottom, while the footer includes three full-width buttons, two 8px gaps, and 20px plus safe-area padding. Its rendered height therefore exceeds the reservation, so the lower portion of the final content is obscured when scrolled to the end. This violates the 375px fixed-action-bar acceptance criterion.  
   References: `frontend/apps/mobile/src/views/PricingDetailView.vue:24-53`; `frontend/apps/mobile/src/styles/components.css:95-97,534-551`.

## Scope Checks

- Mobile-first visual system: design tokens use the required `#246BFE`, `#F5F7FA`, `#172033`, `#667085`, 16px page gutter, and 8px card/button radius. Normal cards use borders rather than heavy shadows; the only retained shadow is the fixed footer.
- 375 x 812 browser check: login brand, account, password, and Login button were all visible in the first viewport. `documentElement.scrollWidth` and `body.scrollWidth` were both 375px. The login shell screenshot is attached to the review run.
- Empty/error/loading states: Todo, request review, task assignment/detail, test confirmation, pricing list/detail add retryable load-error states. Form submit paths retain inputs on failure. The login error rendering itself is present, but is currently fed by the missing-method failure above.
- Permission/state machine: the reviewed mobile screens only expose Notify Finance at `PRICING_APPROVED`, Receive at `FINANCE_NOTIFIED`, and require a nonblank rejection reason. Backend checks still restrict pricing review to the director or assigned product owner and reject finance notification before approval. No additional state-machine regression was found in the Task 5 diff.
- Reachability: the route definitions and role entrances cover request entry/review, assignment, task/experiment, test, pricing review, and finance inbox, but actual reachability is blocked by the failed login finding.

## Verification Evidence

- `git diff --check 5b1ad85 a0a8e87`: passed.
- `pnpm check:routes`: passed (`30` PC routes, `13` mobile routes).
- `node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts`: passed (`5/5`).
- `pnpm typecheck`: failed. Mobile errors include the missing `loginWithPassword` action above; an existing unrelated `TaskCustomerFeedbackView` optional-value error also appears in the exact commit.
- `pnpm exec playwright test ... --project=mobile-chromium --workers=1`: could not start because this environment lacks the Playwright Chromium headless-shell binary. An in-app-browser 375 x 812 check was run against the isolated Vite server instead; it reproduced the login failure after clicking Login.

No business source was modified during this review.

---

# Task 5 Final Review

**Range:** `a0a8e87..0273668`  
**Reviewed:** 2026-07-12  
**Decision:** Changes requested. Not approved.

## Finding

### Important

1. **The reviewed commit still cannot reach the mobile pricing review page.**  
   `0273668` contains `PricingDetailView` and changes its footer to one row with `审核通过` and `更多操作`, but its mobile router has no `/pricing` or `/pricing/:id` route. Both the pricing list and the mobile todo board still link to `/pricing/${id}`, so those links resolve to no view in the reviewed revision. Consequently, a director cannot reach the pending-pricing screen, open `更多操作`, or exercise the fixed-footer behavior at 375px. This is not introduced by the login/footer repair itself, but it remains an acceptance blocker for the Task 5 pricing-review flow. The current worktree contains unstaged route additions; they are outside `a0a8e87..0273668` and were not counted as part of this decision.

   References: `frontend/apps/mobile/src/router/index.ts:24-48` at `0273668`; `frontend/apps/mobile/src/views/PricingListView.vue:31-39`; `frontend/packages/shared/src/api/index.ts:73-83`.

## Verified Repair Scope

- The mobile Login button calls `auth.loginWithPassword`, which delegates to `api.session.login` and sends `POST /api/v1/auth/login`. Playwright exercised the real local H2 backend and all four `mobile-smoke` cases passed.
- Invalid credentials return HTTP 400 with `AUTH_CREDENTIAL_INVALID` and `账号或密码不正确`; the mobile Login view maps that code to the same persistent alert and toast text.
- The pending-review footer source has exactly two first-row actions (`审核通过` and `更多操作`), with `128px` reserved page-bottom space and a two-column footer grid. The corresponding Node contract test passed. A full 375px route-level footer check cannot pass for the reviewed commit until the missing routes are included.

## Verification Evidence

- `git diff --check a0a8e87 0273668`: passed.
- `pnpm typecheck`: passed for shared, mobile, and PC workspaces.
- `node --test tests/mobile-login-and-pricing-review-contract.test.mts tests/pricing-review-access.test.mts tests/mobile-finance-inbox.test.mts`: passed, 7/7.
- `mvn -q -Dtest=AuthControllerTest,SessionTokenServiceTest test`: passed.
- `pnpm exec playwright test e2e/mobile-smoke.spec.ts --project=mobile-chromium --workers=1`: passed, 4/4, against the local H2 backend.
- Local HTTP checks: valid `rnd_assistant / 123456` login returned HTTP 200; invalid credentials returned the expected `AUTH_CREDENTIAL_INVALID` response and message.

No business source was modified during this final review.

---

# Task 5 Route Repair Review

**Commit:** `26795da`  
**Reviewed:** 2026-07-12  
**Decision:** Approved.

No Critical or Important findings.

## Verification

- `/pricing` (`pricing-list`) and `/pricing/:id` (`pricing-detail`) are registered under the mobile router base and dynamically import the existing `PricingListView` and `PricingDetailView` components.
- The pricing list card target (`/pricing/${file.id}`) and Finance todo entrance (`/pricing`) resolve to those registered routes. The mobile route guard applies the shared pricing role rules to both paths; the focused test confirms access for director, product-owner engineer, and Finance, while denying tester roles.
- `pnpm typecheck` passed for shared, mobile, and PC workspaces.
- `pnpm check:routes` passed: 30 PC routes and 16 mobile routes.
- `node --test tests/mobile-pricing-routes.test.mts` passed: 3/3.
- `pnpm --filter @rnd/mobile build` passed and emitted both `PricingListView` and `PricingDetailView` chunks.

No business source was modified during this review.
