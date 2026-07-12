# Task 1 Report: Pure H5 Mode and Stable Login

## Status

DONE_WITH_CONCERNS

## Changed Files

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/lhr/rnd/service/FeishuProperties.java`
- `backend/src/main/java/com/lhr/rnd/service/FeishuIntegrationService.java`
- `backend/src/test/java/com/lhr/rnd/api/AuthControllerTest.java`
- `frontend/apps/mobile/src/router/index.ts`
- `frontend/apps/mobile/src/views/LoginView.vue`
- `frontend/apps/mobile/src/views/ProfileView.vue`
- `frontend/scripts/check-api-contracts.mjs`
- `scripts/print-lan-urls.mjs`
- `.superpowers/sdd/task-1-report.md`

`frontend/e2e/mobile-smoke.spec.ts` already had unrelated uncommitted changes at task start and was not changed or staged by this task.

## Red Test Evidence

1. Added `webLoginWorksWhenFeishuIsDisabled` before production changes.
2. Ran:

   ```bash
   cd backend
   JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=AuthControllerTest#webLoginWorksWhenFeishuIsDisabled test
   ```

3. Observed the expected failure: OAuth returned `FEISHU_OAUTH_CODE_UNSUPPORTED`; the test expected `FEISHU_DISABLED`.
4. Added H5 login contract assertions before changing the mobile page and router. Ran `cd frontend && pnpm run check:api-contracts`; it failed with `Mobile web login must hide Feishu entry points and collapse test helpers by default`.

## Green Verification

| Command | Result |
| --- | --- |
| `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=AuthControllerTest#webLoginWorksWhenFeishuIsDisabled test` | PASS: 1 test, 0 failures |
| `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=AuthControllerTest test` | PASS: 3 tests, 0 failures |
| `cd frontend && pnpm run check:api-contracts` | PASS |
| `cd frontend && pnpm typecheck` | PASS: shared, PC, and mobile type checks |
| `node scripts/print-lan-urls.mjs` | PASS: prints `http://lhrzp-macbook-air-3.local:5174/m/login` before IPv4 fallback |
| In-app browser at `http://127.0.0.1:5174/m/login` | PASS: default account login reaches `/m/todo`; login page showed only account/password/login and collapsed `测试辅助` |

## Required Playwright Command

Ran:

```bash
cd frontend
pnpm exec playwright test e2e/mobile-smoke.spec.ts --project=mobile-chromium --workers=1
```

Result: blocked before any test page or assertion executed. All four tests failed while Playwright launched the system Chrome channel; Chrome exited with `SIGABRT` and Playwright reported `browserType.launch: Target page, context or browser has been closed`. Investigation confirmed the configured project explicitly uses `channel: "chrome"`, the Playwright cache only contains link metadata rather than an installed managed Chromium, and the existing local development server was not part of the command's startup. The successful in-app-browser verification above covers the H5 account-login path against the existing local service.

## Commit

`db3936a feat: add pure H5 login mode`

## Self-check and Remaining Concerns

- `rnd.feishu.enabled` defaults to `false` because `FeishuProperties` is bound at that existing prefix; OAuth short-circuits with `FEISHU_DISABLED`, and notification dispatch returns a zero-count result without calling an identity client.
- The H5 route no longer promotes arbitrary `?code=` values to the callback route; the callback route remains available for direct compatibility access.
- `print-lan-urls.mjs` now uses macOS `LocalHostName` when available, falling back to `os.hostname()` elsewhere, and prints IPv4 as a backup address.
- The Playwright channel crash is an environment/tooling concern. It should be resolved by repairing the system Chrome channel or changing the test environment to an installed Playwright browser before relying on that command as a gate.
