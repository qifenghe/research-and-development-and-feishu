# Task 9 report — revision-aware mobile read-only view and permissions

## Implemented

- Split process access by exact endpoint pattern. `RND_ENGINEER` and `RND_DIRECTOR` retain draft read/save, submission check, submit, revision restore, artifact generation and download. `TESTER` and `QA_TESTER` can only list/read immutable formal revisions and list/download their Formula/SOP artifacts. Finance remains restricted to its linked pricing detail/source summary and receives no process-plan permission.
- V23 upgrade now removes legacy tester draft/submission-check reads while preserving and adding explicit formal revision/artifact reads. No `/process-plan/**` wildcard was introduced.
- Draft GET now enforces the same server-side owner/director boundary as draft mutation, preventing another engineer from reading an assigned engineer's mutable graph.
- Mobile read-only rendering now shows formal revision status, intermediate output flow and source links, critical-control definitions/status, per-major yield, chained finished yield, and ready Formula/SOP downloads. It adds no process drag/edit/measurement/submission/generation operation; read-only yield selection is disabled.
- Mobile role loading uses the mutable process-plan endpoint only for authorized R&D roles. Testing roles resolve the newest formal revision and its artifacts through revision endpoints.

## TDD evidence

- RED first exposed tester draft/control access, cross-owner draft GET, and missing mobile formal output/flow fields.
- GREEN regression coverage was added to `RolePermissionServiceTest`, `SchemaMigrationTest`, `SessionAuthenticationInterceptorTest`, and the mobile source contract.

## Fresh verification

- Backend focused authorization/migration/controller suite: `RolePermissionServiceTest,SchemaMigrationTest,SessionAuthenticationInterceptorTest,ProcessPlanControllerTest` — PASS.
- Frontend Node suite: 80 passed, 0 failed.
- Shared, PC and mobile direct TypeScript checks — PASS.
- Mobile Vite production build: 457 modules transformed — PASS.
- API contract and route checks: PASS (`30` PC routes, `16` mobile routes).
- `git diff --check`: PASS.

## Risk note

- The mobile view intentionally lists only READY artifacts; failed generation records stay in the PC R&D output center where regeneration is authorized.
- Formal revision history is read-only by role at the endpoint layer. Draft ownership is additionally enforced in the process service; tester formal access is not coupled to mutable draft ownership.

## Review fix round 1

- `rnd-tasks/{id}/detail` now derives role and operator only from the authenticated session. TESTER/QA receives no experiment form before a formal revision exists; after submission it receives a formal-revision-bound shell with draft summary, formula, legacy steps and output measurements removed by the server.
- Submission check and revision list/detail now require a trusted principal. Submission check is owner/director draft access only; revision history is owner/director for R&D and immutable formal read for TESTER/QA. Revision IDs remain constrained by their form ID.
- TESTER/QA artifact lists now use a READY-only public projection containing only ID, revision ID, type, document version, status and generation time. Failure reasons, storage keys, hashes, byte size and generator identity remain internal to the R&D output center.
- Mobile experiment routes now watch task ID immediately, reset A before loading B, bind local drafts to the active task, and fence every asynchronous detail/revision/artifact/template/download/save continuation by request generation. A deferred-response regression proves that a late A response cannot replace B.

### Review-fix verification

- Backend fresh suites: `SchemaMigrationTest` 9/9, `RolePermissionServiceTest` 3/3, `SessionAuthenticationInterceptorTest` 15/15, `ProcessPlanControllerTest` 8/8.
- Frontend Node suite: 81 passed, 0 failed, including the A→B late-response regression.
- Shared, PC and mobile TypeScript checks: PASS.
- Mobile production build: 458 modules transformed, PASS.
- API contract and route checks: PASS (`30` PC routes, `16` mobile routes).
- `git diff --check`: PASS.

## Review fix round 2

- Attachment upload and notify-test now capture an immutable action context before their first await: task ID, route generation, source form ID, local-draft key and operator identity. The draft payload used by an action is also snapshotted before asynchronous work begins.
- Draft creation returns its captured A form directly to the caller. Upload and notify targets are therefore never re-derived from a reactive task B detail after route reuse.
- Every save, upload and submit continuation verifies the same action context before mutation or presentation. A stale continuation silently stops without uploading/submitting another form, publishing a toast, clearing cache, changing hydration/loading flags or navigating.
- A real mounted `ExperimentFormView` test uses Vue's memory router and deferred API promises. It covers A draft-save → route B → late A completion and A submit-test → route B → late A completion, asserting no cross-form call, unchanged B cache/route/hydration and no stale toast publication.

### Round-2 verification

- Mounted action-race suite: 2 passed, 0 failed.
- Full frontend Node suite: 83 passed, 0 failed.
- Shared, PC and mobile TypeScript checks: PASS.
- Mobile production build: 458 modules transformed, PASS.
- API contract and route checks: PASS (`30` PC routes, `16` mobile routes).
- `git diff --check`: PASS.
