# Task 4 Independent Review

Reviewed `0dd8258..1d7bf73` against `task-4-brief.md`, `task-4-report.md`, and the supplied review diff. No business code was changed.

## Findings

### Critical

1. **Finance can still obtain an unreviewed pricing file through export and archive endpoints.** `GET /api/v1/reports/pricing-files/{id}/export` is granted to `FINANCE` ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:172)) but its controller does not resolve the session role ([ReportExportController.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/api/ReportExportController.java:38)). It calls the role-less `downloadPricingFile(id)` overload ([ReportExportService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/ReportExportService.java:188)), thereby skipping the FINANCE visibility check in the role-aware overload ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1775)). In addition, every generated file is immediately archived ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:2551)); FINANCE is permitted to list and download archives ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:175)), while both archive methods have no status/role check ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1750)). This violates the required rule that unreviewed files cannot be visible or downloadable by finance through *any* interface.

2. **The migration does not update pre-existing persisted workflow rules, so an upgraded database can no longer generate a pricing file.** V13 only converts `pricing_file.status` ([V13__add_pricing_review.sql](/Users/lhrzp/Documents/研发bom/backend/src/main/resources/db/migration/V13__add_pricing_review.sql:6)). Existing installations retain the old `SAMPLE_COMPLETED + REQUEST_PRICING -> PRICING_FILE_GENERATED` rule; `WorkflowDrivenSampleStatusMachine` prefers that DB rule ([WorkflowDrivenSampleStatusMachine.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/WorkflowDrivenSampleStatusMachine.java:21)) and then `SampleStatus.valueOf("PRICING_FILE_GENERATED")` fails because the enum was removed. Defaults are only inserted when a workflow has no rules ([WorkflowSettingsService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/WorkflowSettingsService.java:52)), so normal initialization does not repair upgraded data. V13 needs a data migration for the changed workflow rules, plus a migration-from-V12 test with seeded legacy rules.

### Important

1. **A product owner cannot complete the advertised self-review flow from either PC or mobile.** The service permits an `RND_ENGINEER` whose server-derived name equals `productOwnerName` to review ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:2764)), but PC route access for `/pricing/:id` excludes `RND_ENGINEER` ([permissions/index.ts](/Users/lhrzp/Documents/研发bom/frontend/packages/shared/src/permissions/index.ts:43)), as does mobile ([permissions/index.ts](/Users/lhrzp/Documents/研发bom/frontend/packages/shared/src/permissions/index.ts:65)). The RND engineer has no pricing list/detail permission either ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:130)). The review controls added in both detail screens are therefore unreachable to a product owner through the supported clients.

2. **Reviewer identity falls back to client-controlled data whenever session authentication is disabled.** The endpoint accepts a required `reviewerName` in the request ([ReviewPricingFileRequest.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/api/ReviewPricingFileRequest.java:5)) and, without a principal, treats it as an `RND_ENGINEER` ([SampleWorkflowController.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java:313)). The test profile deliberately disables session authentication ([application-test.yml](/Users/lhrzp/Documents/研发bom/backend/src/test/resources/application-test.yml:15)); in any such deployment a caller can submit the product owner's name and self-authorize. The review endpoint should require a server session principal and should not trust or require `reviewerName` from the client.

3. **The PC finance inbox still queries the deleted `GENERATED` state.** [FinanceView.vue](/Users/lhrzp/Documents/研发bom/frontend/apps/pc/src/views/shipment/FinanceView.vue:36) requests `status=GENERATED`, while generated files are now `PENDING_PRICING_REVIEW` and finance-visible files are `FINANCE_NOTIFIED`/`FINANCE_RECEIVED`. The PC page will be empty after this change, whereas mobile correctly uses the new finance statuses ([PricingListView.vue](/Users/lhrzp/Documents/研发bom/frontend/apps/mobile/src/views/PricingListView.vue:62)).

### Minor

1. **`notifyFinance` is not retry-idempotent.** The first call moves the file to `FINANCE_NOTIFIED`; a repeated delivery then fails the `PRICING_APPROVED` precondition ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1655)) instead of returning the existing notification. A lost response leaves PC/mobile unable to tell that the handoff succeeded. The review and receive endpoints are idempotent, but this adjacent handoff request is not.

## Verified Behavior

- Generation creates `PENDING_PRICING_REVIEW`; notification and receipt service paths reject inappropriate statuses.
- The service allows product-owner self-review, rejects unrelated R&D, requires a rejection reason, retains rejected versions, and makes repeated review/receipt idempotent.
- The existing resample path remains responsible for creating a new sample version; Task 4 does not mutate the locked experiment form.

## Test Evidence

Passed:

```text
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest,RolePermissionServiceTest,SchemaMigrationTest test
```

The current tests do not cover the finance export/archive bypass or an upgrade that already contains persisted legacy workflow rules.

---

## Final Independent Review (`1d7bf73..686bde8`)

Reviewed the supplied fix diff and current committed implementation. No business code was changed.

### Decision

**Not Approved: 2 Important findings remain.** No Critical finding remains.

### Important

1. **An upgraded installation still does not grant product owners the new pricing list/detail permissions.** The added `RND_ENGINEER` permissions exist only in the code defaults ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:127)); initialization inserts defaults only when that role has *no* persisted permissions ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:38)). Existing databases already have `RND_ENGINEER` role-permission rows, so they retain the pre-Task-4 set that lacks `GET /pricing-files` and `GET /pricing-files/{id}/detail`. V13 has no corresponding `role_permission` data migration. Thus the PC/mobile route change works on a fresh database but the original “product owner cannot reach self-review” failure remains after a normal upgrade.

2. **The server-side product-owner boundary can be bypassed through report export and archives.** `hasPermission` grants every `RND_ENGINEER` all report exports before consulting the persisted rule set ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:208)). The report endpoint forwards only the role, not the session name ([ReportExportController.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/api/ReportExportController.java:40)); `downloadPricingFile` applies no owner check and only restricts `FINANCE` ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1831)). The archive list/download path likewise receives only a role ([SampleWorkflowController.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java:382)) and filters only finance visibility ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1798)). Consequently, an R&D engineer who knows another version/file ID can export or download another product owner's pricing archive, defeating the required server-side owner isolation.

### Minor

1. **The new product-owner detail screen advertises a download action that RBAC rejects.** Both clients always render the pricing download control ([PricingDetailView.vue](/Users/lhrzp/Documents/研发bom/frontend/apps/pc/src/views/shipment/PricingDetailView.vue:18), [PricingDetailView.vue](/Users/lhrzp/Documents/研发bom/frontend/apps/mobile/src/views/PricingDetailView.vue:22)), and the detail payload also includes `DOWNLOAD_PRICING_FILE` for `RND_ENGINEER` ([SampleWorkflowService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1497)). But the `RND_ENGINEER` permission set omits that route ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:127)), while the fallback role test also excludes it ([RolePermissionService.java](/Users/lhrzp/Documents/研发bom/backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java:281)). A product owner can open the page but gets `403` when clicking download.

### Verified Fixes

- The two original finance-only report/archive bypasses are closed: export passes the server session role and finance archive/list/download visibility is restricted to `FINANCE_NOTIFIED` or `FINANCE_RECEIVED`.
- V13 now migrates legacy pricing workflow rules, and the V12-to-V13 migration regression test verifies the migrated transitions.
- Review requires a server `SessionPrincipal`; client `reviewerName` is ignored, and the focused controller test covers the no-principal rejection and spoofed JSON case.
- The PC finance inbox queries `FINANCE_NOTIFIED` and `FINANCE_RECEIVED` rather than `GENERATED`.
- Repeating `notifyFinance` while the file remains `FINANCE_NOTIFIED` returns the original notification without adding another record.

### Fresh Test Evidence

Passed:

```text
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=SampleWorkflowControllerTest,RolePermissionServiceTest,SchemaMigrationTest,ReportExportControllerTest,SessionAuthenticationInterceptorTest test

cd frontend
node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts
```

The focused tests pass, but they initialize a fresh permission table and do not exercise product-owner access through report export or archives; they therefore do not detect the two Important findings above.

---

## Final Independent Review (`686bde8..536338a`)

Reviewed the latest supplied diff, current committed implementation, and the Task 4 brief. No business code was changed during this review.

### Decision

**Approved. No Critical or Important findings remain.**

### Verified Closure

- **Upgrade compatibility:** V13 backfills pricing list, detail, and download permissions when an existing `RND_ENGINEER` role already has persisted permissions. The V12-to-V13 migration regression seeds a legacy permission row and verifies all three additions.
- **Product-owner isolation:** PC/mobile list and detail routes admit `RND_ENGINEER`, while the server derives role and name from `SessionPrincipal` for list, detail, direct download, pricing report export, archive list, and archive download. Each R&D-engineer path applies the same product-owner check; the controller regression covers own-file success and another product owner's rejection for all five read paths.
- **Download RBAC/UI consistency:** `RND_ENGINEER` has the direct-download permission in both configured and fallback RBAC. PC and mobile render the download action only for the same permitted role set, including the responsible R&D engineer.
- **Earlier findings retained as closed:** finance export/archive visibility remains limited to notified or received pricing, legacy workflow rules are migrated to the review states, review requires a server session principal, the PC finance inbox uses finance-visible statuses, and repeated finance notification is idempotent.

### Fresh Test Evidence

Passed:

```text
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=SchemaMigrationTest,SampleWorkflowControllerTest,RolePermissionServiceTest,ReportExportControllerTest test

cd frontend
node --test tests/mobile-finance-inbox.test.mts tests/pricing-review-access.test.mts
```

The backend command exited `0`; the frontend suite passed 5/5 tests. `git diff --check 686bde8..536338a` also completed without whitespace errors.
