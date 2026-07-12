# Task 4 Report: Pricing Review and Finance Handoff

## Delivered

- Added the pricing review state machine: `PENDING_PRICING_REVIEW`, `PRICING_APPROVED`, `PRICING_REJECTED`, `FINANCE_NOTIFIED`, and `FINANCE_RECEIVED`.
- Added `POST /api/v1/pricing-files/{id}/review`. R&D directors can review any file; R&D engineers can review only files for which they are the product owner, including their own files.
- Blocked finance notification until approval. Finance list/detail/download/receive access is limited to notified or received files.
- Persisted reviewer, review timestamp, comment, and rejection reason. Review actions are audited and repeated review is idempotent.
- Rejection requires a reason. A rejected file permits a new pricing version (`核价V2`, `核价V3`); locked experiment/sample versions remain unchanged.
- Updated shared API/types plus mobile and PC pricing detail entry points for approve/reject and review metadata.

## Migration Note

The plan named this migration `V12`, but a concurrent existing `V12__convert_finished_yield_to_percent.sql` was detected. This task therefore uses `V13__add_pricing_review.sql` to keep Flyway migration versions unique.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest,RolePermissionServiceTest,SchemaMigrationTest test` -- passed, 78 tests.
- `node --test tests/mobile-finance-inbox.test.mts` -- passed, 2 tests.
- `pnpm typecheck` -- passed for shared, mobile, and PC workspaces.
