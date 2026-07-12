# Task 6 Final Review: H5 Authorization Boundaries

## Scope

Reviewed commit `0b47d9c` (`fix: enforce H5 authorization boundaries`) and
`.superpowers/sdd/task-6-report.md`.

## Result

**Approved**

No Critical or Important findings.

## Verification Notes

- Optional authentication no longer exempts business APIs from session validation or RBAC. With `auth-required=false` and the test-only bypass disabled, anonymous business access returns `401`, an unauthorized role returns `403`, and a permitted role succeeds.
- Public login remains anonymous; `/actuator/**` remains outside the authentication interceptor. A live local H2 instance returned `HTTP 200` with `{"status":"UP"}` from `/actuator/health`.
- Pricing notification and receipt require a server-side session principal. Receipt persists the authenticated user's name rather than the request-body `receivedBy` value.
- The committed Playwright API E2E uses real `/api/v1/auth/login` sessions against a live local H2 backend with the test-only bypass disabled. It asserts non-empty, behavior-specific values for: unreviewed notification rejection (`400`, `PRICING_FILE_REVIEW_REQUIRED`), unauthorized review (`403`, `SESSION_ROLE_FORBIDDEN`), retained `A0-核价V1` plus generated `A0-核价V2`, and a failed test yielding locked A0 plus A1.
- Focused backend verification passed:
  `mvn -q '-Dtest=AuthControllerTest,SessionAuthenticationInterceptorTest,SampleWorkflowControllerTest#failedInternalTestCreatesNextSampleVersionAndResamplingTask+pricingNotificationAndReceiptUseServerSessionPrincipal' test`.
- Focused real-session E2E verification passed: `pnpm --dir frontend exec playwright test e2e/mobile-pricing-security-version.spec.ts --project=mobile-chromium --workers=1` (`2 passed`).
