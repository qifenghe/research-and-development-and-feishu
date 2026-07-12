# Task 6 Report: H5 Five-Role Pricing Workflow Regression

## Status

Completed.

## Delivered Scope

- The repeatable H5 journey now covers: assistant request creation, director approval and assignment, engineer acceptance and pricing-first experiment entry (main ingredient, ingredient, process loss, finished output), internal test lock, pricing-file generation, product-owner approval, finance notification, finance download, and finance receipt.
- Before a pricing approval, the finance H5 inbox is asserted to be empty for the new record. The product owner approves the generated file before the assistant can notify finance.
- The existing controller tests were run for the required API branches: approval blocking and non-owner rejection, finance visibility restrictions, pricing rejection followed by `A0-核价V2`, and failed testing followed by an `A1` resampling task.

## Root Cause and Repair

The original closed-loop browser test still targeted removed form tabs and therefore timed out before the pricing fields were filled. It now uses the current pricing-first H5 controls and persists the complete required payload.

The first real H5 flow then found a production blocker: with `rnd.session.auth-required=false`, `SessionAuthenticationInterceptor` returned before parsing a supplied Bearer token. The pricing review endpoint correctly requires the server-side principal, so a valid H5 product-owner login received `SESSION_PRINCIPAL_REQUIRED` and could not approve. The interceptor now verifies and attaches a supplied token in optional-auth mode, while requests without a token remain allowed there and strict mode keeps its existing RBAC enforcement.

## Red-Green Evidence

1. `node scripts/run-mobile-closed-loop.mjs` initially failed at the obsolete `配方` selector, then at the missing H5 session principal during pricing approval.
2. Added `SessionAuthenticationInterceptorTest#attachesBearerPrincipalWhenAuthorizationIsOptional`; it failed with a null `sessionPrincipal` attribute before the repair.
3. The focused test passed after the interceptor repair. The full H5 closed loop then passed with all three local services available.

## Changed Files

- `backend/src/main/java/com/lhr/rnd/api/SessionAuthenticationInterceptor.java`
- `backend/src/test/java/com/lhr/rnd/api/SessionAuthenticationInterceptorTest.java`
- `frontend/e2e/mobile-closed-loop.spec.ts`
- `scripts/run-mobile-closed-loop.mjs`
- `docs/mobile-uat-checklist.md`
- `.superpowers/sdd/task-6-report.md`

## Verification

| Command | Result |
| --- | --- |
| `node scripts/run-mobile-closed-loop.mjs` | PASS: confirms 8080, 5173, 5174; 1 H5 five-role workflow passed. |
| Focused H5/API regression | PASS: 5 tests, including approval gate, V2 regeneration, A1 resampling, finance restriction, and optional-session principal injection. |
| `cd backend && mvn test` | PASS: 148 tests, 0 failures, 0 errors. |
| `cd frontend && node --test tests/*.test.mts` | PASS: 27 tests. |
| `cd frontend && pnpm typecheck` | PASS: shared, PC, and mobile. |
| `cd frontend && pnpm build` | PASS: PC and mobile production builds. |
| `cd frontend && pnpm check:api-contracts && pnpm check:routes` | PASS. |
| `cd frontend && pnpm exec playwright test --workers=1` | PASS outside the sandbox: 12 PC/mobile tests. |

## Environment Note

The full Playwright command cannot launch the configured system Chrome inside the filesystem sandbox: Chrome exits with `SIGABRT` before any test starts and cleanup reports `kill EPERM`. The same command was rerun outside the sandbox and passed all 12 tests. The dedicated closed-loop script also passed locally.

## Commit

`test: verify H5 pricing workflow`
