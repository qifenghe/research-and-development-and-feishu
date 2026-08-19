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
