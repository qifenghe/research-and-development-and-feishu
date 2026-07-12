# Task 5 Report: Mobile H5 Clean Office Experience

## Scope delivered

- Unified the mobile design tokens around the approved clean-office palette, 16px page rhythm, 8px cards and buttons, dense lists, and compact status badges.
- Reworked login, dashboard, demand entry/review, task assignment/detail, internal testing, experiment-facing action areas, pricing list/detail, and finance inbox presentation without changing permission checks or workflow transitions.
- Kept web account/password login as the only visible entry. No Feishu entry point or client detection was added.
- Added explicit loading, compact empty, and retryable error states across the covered list and detail pages. Empty states no longer use a large decorative illustration.
- Enforced a single primary fixed action per state by demoting downloads and alternative task actions to secondary buttons when a workflow action is available.
- Added dashboard status symbols (审、样、测、价) and a 375px page-contract test for login visibility, three dashboard stats, one priority task, and no horizontal overflow.

## Validation

- `pnpm typecheck` passed.
- `node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts` passed: 5 tests.
- `pnpm exec playwright test e2e/mobile-flow.spec.ts e2e/mobile-smoke.spec.ts --project=mobile-chromium --workers=1` passed: 7 tests.
- Visual checks at 375 x 812 covered login, dashboard, demand entry, and pricing empty state. The login controls fit in the first viewport; no horizontal overflow was observed; fixed navigation did not cover dashboard content.
- `node scripts/run-mobile-closed-loop.mjs` confirmed backend and mobile health endpoints, then launched Playwright. The current execution channel returned before the five-role run emitted a final pass/fail result, so this run is intentionally not reported as passing.

## Notes

- Existing role permissions and the pricing review state machine remain unchanged. The pricing detail only exposes finance notification after the existing approved state, and finance continues to see download plus confirmation actions only for notified files.

## Review Fixes

- Connected mobile password login to `POST /api/v1/auth/login`, including persisted session state and a non-blocking session refresh.
- Included the minimal web-account backend path: public login route, password verification, default H5 accounts, account migration, and username-aware session tokens.
- Updated mobile smoke coverage to click Login and wait for the actual `POST /api/v1/auth/login` request before asserting the Todo screen.
- Reworked pending pricing review into a single fixed row: `审核通过` remains primary while `更多操作` contains download and rejection. The existing 128px footer reservation exceeds this one-row footer height at 375px width.
- Added a Node page contract covering both the password-login API chain and the single-row review footer.

## Review Verification

- `mvn -q -Dtest=AuthControllerTest,SessionTokenServiceTest test` -- passed.
- `pnpm typecheck` -- passed for shared, mobile, and PC workspaces.
- `node --test tests/mobile-login-and-pricing-review-contract.test.mts tests/pricing-review-access.test.mts tests/mobile-finance-inbox.test.mts` -- passed, 7 tests.
- `pnpm check:api-contracts` -- passed.
- `pnpm exec playwright test e2e/mobile-smoke.spec.ts --project=mobile-chromium --workers=1` -- passed, 4 tests, including credential login through the real local backend.

## Final Route Closure

- Registered the mobile `pricing-list` (`/pricing`) and `pricing-detail` (`/pricing/:id`) routes with the existing `PricingListView` and `PricingDetailView` components. They remain behind the router's existing `canAccessRoute(role, path, "mobile")` guard, using the Task 4 pricing permission matrix unchanged.
- Added `mobile-pricing-routes.test.mts`: it verifies the pricing-card destination has registered list/detail targets, permits `RND_DIRECTOR`, `RND_ENGINEER` (product owner), and `FINANCE`, and rejects `TESTER` and `QA_TESTER`.
- `pnpm check:routes` passed: 30 PC routes and 16 mobile routes. The focused Node suite passed 10/10, and `pnpm typecheck` passed for shared, mobile, and PC.
- The requested current-run mobile smoke was attempted. In the sandbox Chrome could not launch (`SIGABRT`/`EPERM`); outside the sandbox it launched, but first failed because port 5174 was not running. After starting the existing Vite server, the login-page case passed while login-dependent cases failed because the existing Vite `/api` proxy targets `localhost:8080`, where no backend is listening. The available service on `8081` returns `403` for the login endpoint. This environment/proxy mismatch predates and is outside the pricing-route change.
