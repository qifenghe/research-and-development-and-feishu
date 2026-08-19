# Task 5 report — immutable formal process revisions

## RED

- Added `ProcessRevisionServiceTest` before the service existed. `mvn -q -Dtest=ProcessRevisionServiceTest test` failed at test compilation because `ProcessRevisionService` and its submit command did not exist.
- Added controller contracts for submit, revision list/detail, new-draft, trusted session identity, and the spoofed `submittedBy` payload field.
- Added migration and real-session authorization coverage for the V22-to-V23 upgrade and revision read/write roles.

## GREEN

- Formal submission now requires `confirmed=true`, a matching draft `versionNo`, and the authoritative backend `ProcessSubmissionValidator` result. It stores stable business codes including `PROCESS_SUBMISSION_CONFIRMATION_REQUIRED`, `PROCESS_PLAN_NOT_READY`, and `PROCESS_PLAN_VERSION_CONFLICT`.
- Submission atomically moves the matching DRAFT plan to `SUBMITTED`; this compare-and-set protects the draft version and prevents concurrent double submissions. Revision numbers are unique per form and the snapshot insert is append-only.
- `ProcessRevisionService` serializes the normalized, database-loaded complete `ProcessPlan` through the configured project `ObjectMapper`, hashes its UTF-8 JSON bytes with SHA-256, and only reads snapshot rows thereafter.
- New-draft restores only a revision belonging to the requested form, requires a nonblank reason, increments the editable plan version, and persists `source_revision_id` plus `change_reason` on `experiment_process_plan` in V23.
- The controller ignores the submitted-by request payload and takes the name from `SessionAuthenticationInterceptor`'s server-side principal. Revision GET permissions remain with `RND_DIRECTOR`, `RND_ENGINEER`, and `TESTER`; submit/new-draft POST permissions remain with the existing director/engineer editors.

## Verification

- `mvn -q -Dtest=ProcessRevisionServiceTest,ProcessPlanServiceTest,ProcessPlanControllerTest,ProcessSubmissionValidatorTest,SessionAuthenticationInterceptorTest,RolePermissionServiceTest,SchemaMigrationTest test` — PASS.
- `node frontend/scripts/check-api-contracts.mjs` — PASS.
- Shared, PC, and mobile offline TypeScript checks — PASS.
- `git diff --check` — PASS.

## Self-review

- Snapshot coverage exercises `balanceToleranceKg`, step outputs, material source data, and calculated fields through the full persisted `ProcessPlan`; prior Task 4 round-trip coverage covers controls and measurements.
- A concurrent service test confirms exactly one same-version submit creates a revision; the other receives the version-conflict path.
- Revision detail and draft restoration call `find(formId, revisionId)`, so a valid revision ID from another form is not disclosed or restored.
- No `node_modules` symlink is staged.

## Follow-up concern

- The V23 migration is still unshipped in this task branch, so the new draft provenance columns and permission seeds are intentionally appended there. Once V23 has been released, any subsequent alteration must use a new migration rather than edit V23.

## Review remediation — round 1

### RED

- Added regression tests for historical revision reopening, tampered `snapshot_json`, persisted-draft change-reason override, audit rows, same-version concurrent saves, save-vs-submit, cross-assignee authenticated POSTs, and TESTER reads after a reopen.
- The initial targeted RED run failed in the expected places: historical reopening returned `PROCESS_PLAN_VERSION_CONFLICT`; a submit-time reason override was accepted; tampered JSON was returned; audit rows were absent; an out-of-assignment engineer received HTTP 200; and TESTER received the live `DRAFT`.

### GREEN

- `ProcessPlanService.save` now first CAS-updates `id + version_no + DRAFT` before deleting any child graph. Failed ownership acquisition returns `PROCESS_PLAN_VERSION_CONFLICT`, and the transaction rolls back before destructive child work.
- Formal write routes pass the server-derived `SessionPrincipal` to the revision service. `RND_ENGINEER` must match `experiment_form.task_id -> rnd_task.assignee_name`; `RND_DIRECTOR` is explicitly permitted. The stable denial code is `PROCESS_PLAN_FORM_FORBIDDEN`.
- Reopening uses the selected form-scoped revision as the content/provenance source, but CASes the current live `SUBMITTED` header and increments its version. A historical revision can therefore be reopened after later revisions exist.
- TESTER base-plan reads resolve the latest formal snapshot and reject forms without a formal version using `PROCESS_FORMAL_REVISION_REQUIRED`; editor reads remain live-plan reads.
- Every revision row now recomputes SHA-256 over raw UTF-8 `snapshot_json` and compares with `MessageDigest.isEqual`; mismatch returns `PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR` before detail return or draft restore.
- Submit resolves one canonical change reason: a persisted draft reason cannot be overridden (`PROCESS_CHANGE_REASON_CONFLICT`), and the resolved provenance is written consistently to the live plan, revision metadata, and hashed snapshot.
- Submit and new-draft write `PROCESS_PLAN_SUBMITTED` / `PROCESS_PLAN_DRAFT_CREATED` audit records in their enclosing transactions, with form, revision/source revision, trusted operator, and reason. Rejected/tampered restore paths leave no audit row.

### Fresh verification

- `mvn -q -Dtest=ProcessRevisionServiceTest,ProcessPlanServiceTest,ProcessPlanControllerTest,ProcessSubmissionValidatorTest,SessionAuthenticationInterceptorTest,RolePermissionServiceTest,SchemaMigrationTest test` — PASS (includes concurrency, real-token ownership, tester-state, audit, and V22→V23 schema coverage).
- `node scripts/check-api-contracts.mjs` — PASS.
- `packages/shared/node_modules/.bin/tsc -p packages/shared/tsconfig.json --noEmit` — PASS.
- `apps/pc/node_modules/.bin/vue-tsc -p apps/pc/tsconfig.json --noEmit` — PASS.
- `apps/mobile/node_modules/.bin/vue-tsc -p apps/mobile/tsconfig.json --noEmit` — PASS.
- `git diff --check` — PASS.
