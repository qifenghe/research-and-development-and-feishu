# Task 6 Report — Standard Formula and SOP Artifacts

## RED / GREEN

- RED: added `ProcessArtifactServiceTest` before the production artifact model/service existed; `mvn -q -Dtest=ProcessArtifactServiceTest test` failed at test compilation for the missing types.
- GREEN: implemented immutable-revision artifact generation, then the same focused suite passed with real `XSSFWorkbook` and `XWPFDocument` parsing.
- RED: added the artifact controller route test before mapping the endpoints; generation request resolved to no endpoint and failed.
- GREEN: added list/generate/download mappings with session identity, stable content type and attachment disposition; the controller test passed.
- RED: added role assertions for generate/read/download before permission rules existed; the role test failed as expected.
- GREEN: added narrow V23/default permission rules and a strict-auth session test; the role and real-session tests passed.

## Delivered

- `FORMULA_XLSX` and `SOP_DOCX` only generate from a formal revision scoped by both form and revision ID.
- Formula derives external materials from `ProcessRecipeService.aggregate(snapshot)`, excludes step outputs, uses numeric workbook cells, preserves a displayed 100 kg total, and embeds source revision, generator, yield and change reason.
- SOP orders major processes/minor steps, shows external inputs and intermediate handoff separately, production controls/deviation action, yield summaries, and a measurement-only trace appendix carrying tool/basis/confirmation fields.
- A row lock plus unique document-version key serializes concurrent same-type generation. Each attempt receives the next version, while old metadata and file bytes remain downloadable.
- Artifact IDs and storage keys are server-generated. Failed writes/rendering create `FAILED` metadata (with failure reason) and do not change the submitted revision. Database failure after file storage triggers best-effort storage cleanup.
- Reads and downloads are form+revision+artifact scoped; missing/corrupt bytes return a domain error and storage path validation remains in the archive abstraction.
- R&D owner/director can generate; tester/QA tester are read-only. Session tests cover owner identity, tester POST denial, and other-engineer denial.

## Verification

| Check | Result |
| --- | --- |
| `mvn -q -Dtest=ProcessArtifactServiceTest,ProcessPlanControllerTest,ProcessRevisionServiceTest,RolePermissionServiceTest,SessionAuthenticationInterceptorTest#enforcesRealSessionRolesAndFormOwnershipForFormalArtifactGenerationAndDownload,SchemaMigrationTest test` | PASS |
| direct shared `tsc`, PC `vue-tsc`, mobile `vue-tsc` | PASS |
| API contract and route checks | PASS |
| `git diff --check` | PASS |

`pnpm typecheck` attempted a registry metadata refresh in this isolated worktree and stopped before typechecking; the direct offline project compilers above are the authoritative result here.

## Self-review / follow-up

- The V23 migration is intentionally amended (not a new migration) because this feature series has not been released; it makes `storage_key` nullable for failed records and adds `failure_reason` plus exact artifact permissions.
- File generation has not been coupled to pricing; pricing remains a later task and formal revisions remain immutable after artifact failure.

## Fix round 1

- RED/GREEN details and review evidence are in `task-6-review.md`.
- V23 now records content SHA-256, byte size, generated user ID and immutable task assignee ID. Downloads fail closed with `PROCESS_ARTIFACT_INTEGRITY_ERROR` for altered or truncated bytes.
- Formal submission preview/validator and artifact generation all reject non-positive external material total with `EXTERNAL_MATERIAL_WEIGHT_REQUIRED`.
- Verification rerun: affected backend validator/artifact/controller/revision/auth/schema tests and offline shared/PC/mobile typechecks plus API/route contracts.

## Fix round 2

- Preserved immutable assignee IDs when internal-test or customer-feedback resampling creates the next task; assignment resolves duplicate active names before mutating the in-memory task pool.
- Restored dependent-first cleanup in `SampleWorkflowControllerTest` while retaining the V23 assignee FK.
- Formula display values now use deterministic largest-remainder allocation at 0.0001 precision, so displayed ratio and 100 kg columns remain non-negative and total exactly `100.0000`.
- Generation now requires a session user ID and records it on failed artifact metadata too. The obsolete implementation note `task-6-review.md` was removed; independent review remains untouched.

## Fix round 3 (in progress)

- V23 adds immutable `audit_log.operator_user_id`; artifact generation/failure and session-backed formal submit/new-draft pass the trusted ID to the audit service.
- Assignment now resolves exactly one active account once before mutation and passes that ID directly to persistence. Workflow fixtures now create a real active assignee with Feishu identity.
- Added a V23 durable cleanup ledger: every generated server key is registered independently, confirmed only after commit, and reconciled before later generation attempts; retry deletion remains constrained to `process-artifacts/` keys with no READY DB reference.

## Fix round 3 completion

- Assignment now resolves exactly one active account exactly once before any task-map mutation, persists that same immutable ID, and restores the prior cache value on transaction rollback. Zero/multiple matches leave both DB and cache pending and can be retried; internal-test and customer-feedback resample tasks retain the original assignee ID.
- `audit_log.operator_user_id` is populated from the trusted session for READY/FAILED artifact generation, formal submission, and new-draft creation. Same-display-name tests prove the immutable owner succeeds while the different-ID account is denied for artifact list/generate/download and revision submit/new-draft.
- `process_artifact_cleanup_ledger` now records `created_at`, `last_attempt`, and `error`. A server key is registered in `REQUIRES_NEW` before rendering/storage; commit confirms it, rollback deletes it, and delete failure remains durable for the next generate-time retry. Startup reconciliation handles crash reservations, while runtime reconciliation only processes rows already proven orphaned, so an uncommitted concurrent reservation cannot be deleted.
- Transaction-template coverage injects a `beforeCommit` failure after `generate` returns and proves artifact/audit rollback, file deletion, and ledger convergence. Additional coverage proves delete-failure retry, crash-orphan cleanup, READY preservation, FAILED version consumption/retry/list/download behavior, immutable audit IDs, five-line/tiny/tie/three-equal exact allocation, exhaustive SOP sentinels, and production-standard/measurement-appendix separation.
- Shared workflow-test cleanup now removes V23 process children before parent forms, eliminating the cross-class FK cleanup failure.

### Fix round 3 verification

| Check | Result |
| --- | --- |
| `ProcessArtifactServiceTest` | PASS, 11 tests |
| `ProcessPlanControllerTest` | PASS, 6 tests |
| `ProcessRevisionServiceTest` | PASS, 11 tests |
| `SampleWorkflowControllerTest` | PASS, 81 tests |
| `SessionAuthenticationInterceptorTest` | PASS, 12 tests |
| `RolePermissionServiceTest` | PASS, 2 tests |
| `ProcessSubmissionValidatorTest` | PASS, 11 tests |
| `SchemaMigrationTest` | PASS, 9 tests; total migration remains V23 |
| direct shared `tsc`, PC/mobile `vue-tsc` | PASS |
| API contract and route checks | PASS |

The workspace `pnpm` wrapper attempted an online metadata/install check and could not run offline; the checked-in workspace binaries were used directly for the three authoritative typechecks. No `node_modules` paths are included in the commit.

## Fix round 4

- V23 keeps the cleanup ledger at migration 23 and now gives each reservation an explicit `RESERVED`/`ORPHANED` state, unique owner token, and future lease. Startup and runtime reconciliation use the same eligibility rule: an unexpired reservation is never touched; rollback first marks its own reservation `ORPHANED`, then deletes immediately; failed deletes retain the orphan for retry.
- Reservation renewal and every confirm/orphan transition match the owner token. Expired reservations are claimed by a conditional state transition, and file deletion rechecks the owner under a row lock, so a delayed request cannot mutate or delete a later owner of the same key.
- Latch-based transaction tests pause generation after durable registration but before storage, and after storage but before commit. Both startup interleavings preserve the live reservation; rollback converges file plus ledger, while commit preserves READY bytes. Additional tests cover expired crash cleanup, expired READY-reference ledger-only cleanup, delete retry, stale owner rejection, and failed post-commit confirmation recovery.
- The SOP regression now creates revision 1, derives a new draft, submits revision 2, and parses the resulting DOCX by concrete metadata, step, control-standard, and trace-appendix tables. Unique sentinels cover source/change/generation metadata, batch bases, major/step/flow/output fields and yields, every production-control field, and every trace field. Production standards exclude confirmation and measurement data while the appendix retains it.

### Fix round 4 verification

| Check | Result |
| --- | --- |
| Combined artifact/controller/revision/workflow/auth/role/validator/schema run | PASS: 147 tests, 0 failures, 0 errors; workflow remains 81 tests |
| `ProcessArtifactServiceTest` | PASS, 15 tests |
| `SchemaMigrationTest` | PASS, 9 tests; migration total remains V23 |
| API contract and route checks | PASS |
| direct shared `tsc`, PC/mobile `vue-tsc` | PASS |
| `git diff --check` | PASS |

## Fix round 5

- Generation now performs a post-store owner check with `SELECT ... FOR UPDATE` on the exact cleanup-ledger key/token/`RESERVED` row. The method uses `MANDATORY`, so the row lock belongs to the outer generation transaction and remains held until its commit or rollback; it is never released by a nested `REQUIRES_NEW` transaction.
- Reconciliation now locks each candidate ledger row first, then rechecks owner, state, lease expiry, and committed READY references under that lock. A future-skewed instance therefore blocks behind active finalization; after commit it removes only the ledger and preserves downloadable READY bytes, while after rollback it removes the bytes.
- If cleanup claims the reservation while storage is still in flight, post-store validation fails, the just-written key is deleted, and the attempt records only FAILED metadata. A stale owner token cannot lock or delay a replacement reservation.
- The previously observed save/submit concurrency flake was a real mixed-snapshot race: submission loaded the plan header and child graph with multiple queries before taking the submission lock. Submission now locks the plan header before loading and validating the graph, so save-first yields a version conflict and submit-first prevents the save from replacing children.

### Fix round 5 verification

| Check | Result |
| --- | --- |
| Combined artifact/controller/revision/workflow/auth/role/validator/schema run | PASS: 151 tests, 0 failures, 0 errors; artifact suite 19 tests (existing 15 plus 4 concurrency regressions) |
| Four artifact lease/owner concurrency regressions repeated 3 times | PASS on all 3 runs |
| Save/submit concurrency regression repeated 5 times | PASS on all 5 runs |
| API contract and route checks | PASS; 30 PC routes and 16 mobile routes |
| direct shared `tsc`, PC/mobile `vue-tsc` | PASS |
| `git diff --check` | PASS |
