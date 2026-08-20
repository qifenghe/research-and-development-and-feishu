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
