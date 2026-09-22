# Task 3 Report — Trial workbench and planned/actual comparison UI

Date: 2026-09-15  
Scope: PC trial workbench only; Task 1 trial persistence and Task 2 submission APIs consumed without backend changes.

## Delivered

- Added an isolated trial API client and an independent `TrialWorkbench` wrapper around the existing `MajorProcessBoard` and `MinorStepWorkspace`. Ordinary trial edits and saves never call the active formal-plan save endpoint.
- Added a compact scheme bar with create, copy, archive/restore, save, purpose, variables, conclusion, recommendation, and collapsed quality/difficulty fields.
- Added the selected-step `计划与偏差（当前步骤）` section. Missing actual values remain `待补充`, not zero; planned material, parameter, major-yield and batch-yield values remain separate from actual graph values.
- Added A/B comparison for quality, difficulty, parameters, formula/input weights and yields. Major matching uses `majorOrigins`; steps require a unique, nonblank `stepCode`. Blank, duplicate or unmatched steps are explicitly marked `不可比较` and are never guessed by display name or row order. Changed primary material or changed weighing-basis note makes the major incomparable. Numeric parameter deltas are only calculated when name and unit match. No financial values are displayed.
- Added continuous-primary-flow validation for actual batch yield, decimal-safe differences, and null/zero separation.
- Added per-user/form/trial draft cache keys plus independent list/detail/mutation/submission generation fences. Save sends stable temporary `id=key` values, preserves local wall-clock measurement timestamps, disables edits while the acknowledgement is in flight, and adopts the complete durable-ID server acknowledgement.
- Added save/discard/cancel handling before scheme switches and route leave, including local recovery caching. A mounted regression proves a dirty A scheme blocks B reads until an explicit discard and then adopts the delayed B response atomically.
- Added explicit trial control-point pass/deviation mode while preserving the formal editor's legacy confirmation behavior by default.
- Added explicit `正式提交预览与确认`; only a successful Task 2 submit response refreshes the formal plan. Added separately fenced formal-revision source metadata and role-gated displaced-draft recovery/copy-to-trial UI.

## Tests and verification

### RED

- Initial focused run: `node --experimental-strip-types --test tests/trial-*.test.mts` failed because `trialDraft.ts` and `trialComparison.ts` did not exist.
- Browser-derived regression: after selecting A as B's baseline, blank `stepCode` values caused an incorrect `与基线方案无可见差异`. A new test first failed because no `INCOMPARABLE` row was emitted, then passed after unmatched/blank/duplicate step groups were reported explicitly.
- Mounted-test harness initially failed before component setup because the lightweight fake document lacked Ant Design's `getElementsByTagName`; the test-only DOM shim was extended minimally. The mounted component behavior then passed.

### GREEN

- Full frontend tests: `node --experimental-strip-types --test tests/*.test.mts` → **134/134 passed**, exit 0 (baseline 116 + 18 Task 3 tests).
- Workspace typecheck: `pnpm typecheck` → shared, PC and mobile passed, exit 0.
- Production preview build: `node scripts/run-rnd-preview.mjs build` → PC and mobile Vite builds passed, exit 0. The existing large-chunk warning remains non-blocking.
- Whitespace validation: `git diff --check` → passed.
- Preview health after stop/build/start: backend `8082`, PC `5183/admin`, mobile `5184/m` all returned HTTP 200.

The preview helper could not see the already-running supervisor from the restricted shell on its first sandboxed stop attempt. Re-running stop/start with process visibility outside the sandbox succeeded. This report records the limitation; process-management scripts were not changed.

## Real browser result (CUA)

URL: `http://127.0.0.1:5183/admin/rnd/tasks/TASK-0005/experiment`  
Test-only form: `EXP-0005` / task `TASK-0005`

- Trial A: `TRIAL-e0f789a8-b567-4b0d-bf9c-6614c6c02cb4`, name `Task3 UI 验收 A`, version 2.
- Trial B: `TRIAL-ed645262-5d37-45e5-b291-c3dc8a823eef`, name `Task3 UI 验收 B`, version 2.
- B was copied with actuals intentionally for the test and is visibly marked by the copy dialog as inherited data that cannot be treated as new evidence.
- A retained planned primary input `100kg`, target major yield `90%`, target batch yield `72%`, and weighing basis `按主料首端投入` after reload.
- B retained planned primary input `99.8kg` after save and full reload; the UI showed actual `100kg` and deviation `+0.2kg`.
- Selecting A as baseline on the rebuilt bundle retained the planned/actual table and showed explicit `不可比较 / 缺少唯一编号` rows for the legacy blank step identifiers. CUA screenshots captured both the `99.8kg / 100kg / +0.2kg` table and the incomparable explanation as tool evidence.

## Known limitation / follow-up boundary

The existing acceptance formal graph has blank `stepCode` values and the current small-step editor does not expose a business-code field. Therefore those copied steps cannot be safely paired for formula-difference attribution. Task 3 deliberately reports them incomparable instead of matching by name/order. Adding durable step lineage or a backend-generated business identifier is outside this UI task.

Formal submission was not performed against this accepted revision during this bounded Task 3 browser pass; the preview/confirmation wiring is covered by source/tests, and Task 6 owns the full multi-role promotion acceptance matrix.

## Scoped files

- `frontend/apps/pc/src/services/trialApi.ts`
- `frontend/apps/pc/src/components/trials/TrialWorkbench.vue`
- `frontend/apps/pc/src/components/trials/TrialComparison.vue`
- `frontend/apps/pc/src/components/trials/trialDraft.ts`
- `frontend/apps/pc/src/components/trials/trialComparison.ts`
- `frontend/apps/pc/src/components/process/ControlPointEditor.vue`
- `frontend/apps/pc/src/components/process/MinorStepWorkspace.vue`
- `frontend/apps/pc/src/components/process/ProcessPlanWorkspace.vue`
- `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`
- `frontend/tests/trial-workbench.test.mts`
- `frontend/tests/trial-workbench-mounted.test.mts`
- `frontend/tests/trial-comparison.test.mts`
- `.superpowers/sdd/2026-09-15-rnd-workbench-and-exports/task-3-report.md`

Unrelated dirty `DemandDetailView.vue`, planning/review notes, generated outputs, local stores, and other user files were not edited or staged.

## Fix round 1 — independent review findings

Base: `1b5c436`

### RED evidence

- `process-plan-autosave.test.mts`: server hydration during an in-flight save reproduced the reported corruption: expected `SUBMITTED v3`, received the late acknowledgement metadata `DRAFT v2`.
- `process-flow-regression.test.mts`: a two-step major with a missing intermediate primary observation returned `80` instead of `null`.
- The first focused run of the new structure-copy and deviation-unit tests failed because `createStructureOnlyTrialSource` and `deviationUnit` did not exist.
- New comparison regressions initially failed for duplicate/missing identities, duplicate origins on both sides, unchanged-value parameter-definition changes, and downstream primary lineage with remapped output IDs.
- The first mounted route-transition regression showed that a same-component parameter update did not open the unsaved-choice flow.

### Per-finding resolution and evidence

1. **Late formal save acknowledgement:** `ProcessPlanSaveCoordinator.hydrateServer` now advances the epoch, cancels timers and detaches the old in-flight promise. `ExperimentFormView.onTrialPromoted` synchronously hydrates the mounted formal workspace before changing modes. Direct coordinator and mounted `ProcessPlanWorkspace` regressions prove an old `DRAFT v2` acknowledgement cannot replace `SUBMITTED v3`, while a later new draft can still save normally.
2. **Unsafe new/recovered copy:** `createStructureOnlyTrialSource` makes an immutable clone, moves applicable material weights, step parameters and yields into `plannedData`, and clears actual input/output/material weights, parameter values, cached yields, measurements and confirmations while retaining graph references and control standards. Both new-from-formal and recovered-draft creation use this helper. The source-immutability/clearing test covers the complete transformation; Task 1 backend/API semantics were not changed.
3. **Inherited distinction:** inherited schemes now retain a persistent `继承实测` badge and warning after reload, including inside the selected-step measurement context. A source regression guards the persistent wording.
4. **Ambiguous comparison:** comparison now keeps identity groups instead of dropping duplicates. Duplicate/missing formula and input IDs, duplicate `majorOrigins`, and missing major origins emit explicit `INCOMPARABLE` rows. Primary material identity no longer falls back to display name; downstream `STEP_OUTPUT` inputs trace through remapped output IDs to a unique external formula/material identity, otherwise become incomparable. Focused regressions cover two duplicate SALT rows, duplicates on both sides, missing IDs, same display name without stable IDs, and remapped downstream lineage.
5. **Incomplete primary flow:** the shared live yield calculator now requires every participating step in the within-major primary chain to have positive observed primary input/output weights, the correct prior-output reference, `continueFlow`, and matching transfer weight. Trial comparison reuses that result, so the board/workspace and comparison cannot disagree. The missing-intermediate regression now returns `null`/pending.
6. **Unsaved transitions:** scheme switching, creation, copying, external open and same-component route updates use the same save/discard/cancel gate. `adoptServer` retains a clean persisted snapshot; discard clears cache and restores that snapshot before destination loading. Mounted regressions cover blocked creation, route-param cancellation, and failed B loading after discarding edited A.
7. **Percentage points:** planned/actual rates still render with `%`; yield arithmetic differences now render with `个百分点` in both the full comparison and selected-step extension. Unit and source regressions cover both surfaces.
8. **Parameter definitions:** name/unit definitions are compared independently of values. A definition-only row remains visible for `92 ℃ → 92 ℉` or a renamed parameter, and numeric subtraction remains suppressed whenever name/unit differ.

The full-plan planned/actual table is now inside a collapsed `全部计划 / 实际（展开查看）` panel; selected-step `计划与偏差` remains the primary editing context.

### Fix-round verification

- Focused RED→GREEN suites: `node --experimental-strip-types --test tests/process-plan-autosave.test.mts tests/process-flow-regression.test.mts tests/trial-comparison.test.mts tests/trial-workbench.test.mts tests/trial-workbench-mounted.test.mts` → passed after the fixes.
- Fresh full frontend tests: `node --experimental-strip-types --test tests/*.test.mts` → **147/147 passed**, 0 failed, exit 0. Direct mounts intentionally emit Vue Router warnings because two harness cases mount outside a router; the dedicated route-param case mounts through a real memory router and passes.
- Fresh workspace typecheck: `pnpm typecheck` → shared, PC and mobile passed, exit 0.
- Production build: `pnpm build` → PC and mobile passed, exit 0. The pre-existing PC large-chunk warning remains non-blocking.
- Whitespace validation: `git diff --check` → passed, exit 0.
- Preview stop/start used the existing helper with process visibility; health after restart: backend `8082`, PC `5183/admin`, and mobile `5184/m` all returned HTTP 200. No preview-process script was changed.

### Bounded UI verification status

The rebuilt preview is healthy, but a fresh post-fix CUA visual pass could not run: CUA returned no browser surfaces (`browsers: []`), and both in-app and URL browser acquisition reported that no browser was available. After those bounded attempts, no further retries or fixture mutations were made. The three presentation changes are covered by compilation plus source regressions; the prior Task 3 browser evidence above remains valid. The separate `TASK-0009` / `EXP-0009` software-only export fixture was not edited.

### Remaining boundaries

- Existing legacy blank `stepCode` records remain explicitly incomparable; this fix does not invent backend lineage.
- Full role/promotion/export acceptance remains Task 6 scope.
- No backend/API semantics, tester/finance access, or unrelated user files were changed.
