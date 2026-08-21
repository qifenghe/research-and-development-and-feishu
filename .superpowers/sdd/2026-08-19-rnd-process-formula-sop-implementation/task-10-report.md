# Task 10 report — compatibility, full verification, and user documentation

## Scope completed

- Rewrote `docs/工艺分层与得率功能使用说明.md` for R&D users in non-technical Chinese.
- Documented the implemented major-process-first workflow, repeatable major/minor templates, step materials, intermediate products, primary-material yield, critical controls, draft/formal revisions, Formula/SOP generation, pricing linkage, mobile read-only behavior, role boundaries, common formal-submission blockers, and historical compatibility.
- Corrected the obsolete guide's finished-yield description. The implemented finished yield is the product of participating major-process yields, not a direct last-output/first-input quotient.
- Corrected the entry description: opening the experiment form goes directly to the process workspace and its major-process board; there is no separate confirmation transition in the normal PC flow.
- Corrected pricing timing: the server selects and pins the then-latest formal process revision when packaging is confirmed and the downloadable pricing workbook is generated. The workflow does not expose a user revision selector.
- No production code or test assertion was changed in Task 10.

## Fresh verification evidence

### Backend

Command:

```bash
cd backend && mvn -q test
```

Result: PASS, exit code 0. Surefire XML totals: **272 tests, 0 failures, 0 errors, 0 skipped** across 34 suites.

Migration evidence observed in the run:

- Flyway validated 23 migrations.
- A clean schema successfully applied V1 through `V23__extend_process_revision_and_outputs.sql` and ended at v23.
- `SchemaMigrationTest.v23SeparatesDraftEditorsFromFormalRevisionReaders` starts from a V22 schema, migrates to V23, verifies legacy child data and foreign keys, checks draft/formal role permission separation, and asserts 23 successful migration rows.

### Frontend tests

Command:

```bash
cd frontend
node --test --experimental-strip-types tests/*.mts
```

Result: PASS, **83 tests, 0 failures, 0 skipped**.

### Frontend type checks

The isolated worktree deliberately uses four `node_modules` symlinks to the source checkout. Direct local binaries were used to avoid a `pnpm` online dependency refresh:

```bash
frontend/node_modules/.bin/tsc -p frontend/packages/shared/tsconfig.json --noEmit
frontend/apps/pc/node_modules/.bin/vue-tsc -p frontend/apps/pc/tsconfig.json --noEmit
frontend/apps/mobile/node_modules/.bin/vue-tsc -p frontend/apps/mobile/tsconfig.json --noEmit
```

Result: shared PASS, PC PASS, mobile PASS; all exit code 0.

### Contracts and routes

Commands:

```bash
cd frontend
node scripts/check-api-contracts.mjs
node scripts/check-routes.mjs
```

Results:

- API contracts PASS: experiment, shipment, and web login contracts match backend DTOs.
- Routes PASS: **30 PC routes and 16 mobile routes**.

### Production builds

Commands:

```bash
cd frontend/apps/pc && node_modules/.bin/vite build
cd frontend/apps/mobile && node_modules/.bin/vite build
```

Results:

- PC production build PASS: 3,318 modules transformed.
- Mobile production build PASS: 458 modules transformed.
- PC emits the existing Rollup advisory that the main minimized chunk is larger than 500 kB; it is a non-failing performance warning, not a correctness failure.

### Repository hygiene

Commands:

```bash
git diff --check
git status --short
git log --oneline --decorate -12
```

Results before commit:

- `git diff --check` PASS.
- The only tracked Task 10 change is the R&D user guide plus this task report.
- Existing untracked worktree scaffolding remains uncommitted and unchanged: the four `frontend/**/node_modules` symlinks and `frontend/test-results/`.
- No unrelated user file was modified.

## Handoff facts

- Implementation range before the documentation commit: `19ef766..7affe08`.
- R&D entry: PC `我的打样任务 → 打样实验单 → 工艺工作台`; the workspace opens directly on the major-process board, and selecting a major process enters its minor steps.
- Formula XLSX and SOP DOCX require a formal process revision and are generated for the selected formal revision.
- Pricing remains in the existing workflow. On packaging confirmation and downloadable workbook generation, the server obtains and pins the then-latest formal process revision on the pricing record; users do not select a revision. Later formal revisions do not alter that generated pricing file's source.
- Tester/mobile formal readers do not receive draft access and only see READY process artifacts.

## Explicit residual risk

- Task 10 did not rerun the retained Playwright browser suite. Task 8 already recorded that Chrome escalation was rejected by the platform usage limit; this report does not claim a fresh browser pass. The fresh frontend Node suite includes mounted Vue route-reuse/action-race coverage, and both production builds pass.
- The PC production bundle size advisory remains; future code splitting may improve initial load time but does not block this feature.
