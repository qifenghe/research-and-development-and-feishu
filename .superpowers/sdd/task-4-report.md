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

## Independent Review Fixes

- Finance export, pricing download, archive list, and archive download now derive the role from `SessionPrincipal`; finance cannot retrieve unreviewed pricing archives, while non-pricing archive entries remain visible.
- V13 now migrates legacy `REQUEST_PRICING` and `NOTIFY_FINANCE` rules and inserts the pricing approval/rejection rules. A V12-to-V13 Flyway regression test verifies the migrated pricing transition is valid.
- `RND_ENGINEER` receives pricing list/detail permissions and PC/mobile route access, with server-side `productOwnerName` checks preventing access to other products.
- The review endpoint requires a server `SessionPrincipal` and ignores any client-supplied reviewer identity. A disabled-auth test profile cannot bypass this requirement through JSON.
- PC finance view queries `FINANCE_NOTIFIED` and `FINANCE_RECEIVED`; repeated finance notification returns the original notification without duplication.

## Independent Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest,RolePermissionServiceTest,SchemaMigrationTest,ReportExportControllerTest test` -- passed, 85 tests.
- `node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts` -- passed, 4 tests.
- `pnpm typecheck` -- passed for shared, mobile, and PC workspaces.

## Final Review Fixes

- V13 now backfills pricing list, detail, and download permissions only when `RND_ENGINEER` already has persisted permissions. The V12-to-V13 migration regression test seeds a legacy permission row and verifies all three additions; fresh installations continue to initialize the complete default matrix.
- Pricing export, direct download, archive list, and archive download now pass both the server-derived role and name. `RND_ENGINEER` access is limited to versions whose `productOwnerName` equals the session name, while R&D directors retain their existing access.
- Added the RND engineer download permission to both the configured and fallback RBAC paths. PC and mobile pricing detail screens now render the download action only for roles allowed to use it, including the responsible R&D engineer.

## Final Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SchemaMigrationTest,SampleWorkflowControllerTest,RolePermissionServiceTest,ReportExportControllerTest test` -- passed, 87 tests.
- `node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts` -- passed, 5 tests.
- `pnpm typecheck` -- passed for shared, mobile, and PC workspaces.
