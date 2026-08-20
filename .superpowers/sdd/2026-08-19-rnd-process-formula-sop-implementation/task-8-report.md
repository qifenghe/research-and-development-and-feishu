# Task 8 report — PC major-process-first workspace

## Delivered

- Replaced the experiment form's primary process editor with `ProcessPlanWorkspace`; legacy formula and process data remain available only in collapsed **历史兼容数据** sections.
- Added a compact Feishu/DingTalk-style workspace with draft/formal status, save state, final yield, native HTML5 major/minor drag ordering, template reuse, explicit edit/copy actions, breadcrumb navigation, and read-only gating.
- Added minor-step editing for parameters, equipment/instructions, external materials, previous-step continuing outputs, primary flow restrictions, outputs, yield summaries, and full multi-measurement KCP editing.
- Added manual and debounced PUT draft saving, hydration suppression, submission check/confirmation/change-reason flow, revision browsing/new draft, and formal-revision Formula/SOP output center. The output center intentionally contains no pricing-generation action.

## Verification

- `node --test --experimental-strip-types tests/*.test.mts` — 60 passed, 0 failed.
- Direct PC `vue-tsc -p apps/pc/tsconfig.json --noEmit` — passed.
- Direct PC `vite build` — passed (existing large-chunk warning only).

`pnpm --filter @rnd/pc typecheck` could not run in this isolated worktree because pnpm attempted an online dependency refresh after detecting linked node modules; the direct installed `vue-tsc` binary was used for the same PC type check.

No browser mock server was available in this worktree, so no Playwright screenshot run was performed.

## Review round 1

- Centralized process-plan PUT handling in `ProcessPlanSaveCoordinator`; it preserves edits made during an in-flight save, merges acknowledgement metadata without replacing the local graph, avoids saving hydration/formal states, and is covered by deferred-promise Node tests.
- The experiment form no longer PUTs the process plan. Its legacy payload/preview/validation now derives from process-plan external materials and legacy summaries whenever the new plan has content; parent save asks the workspace to flush its own queue.
- Formal submit flushes the draft before opening the confirmation/check dialog, uses source-revision provenance for a change reason, and transitions from the immutable revision snapshot rather than treating a formal revision number as a draft CAS value.
- Draft PUT now uses the trusted session principal and enforces engineer ownership (or director access); controller tests cover owner success and cross-engineer denial.
- Critical-control display now mirrors the shared numeric-bound/deviation/retest semantics. Artifact failures show their failure reason and downloads receive a type/revision extension.

## Review round 2

- Bound the process-save coordinator to a concrete form ID, reject undefined-form flushes, fence acknowledgements by form/session epoch, and distinguish authoritative server hydration from a restored local dirty draft.
- New experiment-form IDs rebind then persist the existing local graph; local cache restoration remains dirty. Flush failures now reject so the parent cannot report the composite save as successful.
- Compatibility materials now remain on true historical/virtual legacy plans and layered compatibility rows split a material's PRIMARY/AUXILIARY sources so yield basis weights remain correct.
- Formal snapshots propagate read-only state to both major/minor editors.

## Review round 3

- Added explicit coordinator server/local adoption APIs, detached in-flight saves on form rebind, and made background pre-ID saves inert while preserving manual failure propagation.
- Parent cache restoration now explicitly marks the restored process draft dirty; formal-version new-draft responses are adopted as trusted DRAFT state before further edits.
- Added tested plan-wide preceding-output and broken-reference repair helpers; the minor workspace now uses the whole-plan context for earlier-major output choices and shows final yield plus aggregate external-recipe weight in its live summary.

## Review round 4

- Fenced stale coordinator failures and every route/form/revision/artifact request with explicit generations. Same-record route navigation now resets and reloads all form, plan, cache, save, version, and output state.
- Moved major and minor editing onto immutable whole-plan replacements. Backend-equivalent flow repair now enforces unique output IDs, precedence, continuing-flow and primary compatibility, removes invalid intermediate consumers without externalizing them, lists affected consumers, and safely remaps copied-major internal chains.
- Added full immutable revision snapshot rendering, revision-to-draft restoration, controlled output revision selection, and request-fenced artifact state. Manual historical output selection persists until the next formal submit.
- Added always-focusable read-only major/step navigation, labelled keyboard reorder controls, stable key-based selection, and visible focus rings.
- Expanded pure and source-integration tests for stale deferred failures, route generations, every destructive flow path, earlier-major/internal-copy chains, controlled output state, immutable boundaries, and accessibility markers.

### Round-4 verification

- Frontend Node suite: 71 passed, 0 failed.
- PC `vue-tsc`, Vite production build, API-contract check, and route check passed; build retained only the existing large-chunk warning.
- Backend `ProcessPlanControllerTest` passed against all 23 migrations.
- `git diff --check` passed.

## Review round 5

- Replaced the lossy `[modelValue, hydrating]` level watcher with an explicit `serverProcessHydrationToken`. The parent accepts the server graph, advances the token, waits for the workspace/coordinator to adopt that exact graph as the authoritative baseline, and only then restores a newer local cache as dirty.
- Fixed a real mounted-runtime defect found by the browser harness: Vue reactive proxies are now converted to raw values before structured cloning at workspace/editor boundaries, so immutable local ownership no longer throws `DataCloneError` during component setup or edits.
- Completed flow-repair parity with the backend by clearing and reporting stale `sourceStepOutputId` values on external materials. Continue-flow, primary-output, primary-material, major-copy, and step-copy repairs now show affected consumer names in `Modal.confirm`, commit only after approval, and restore the local clone without emitting on cancellation.
- Added stable pure `diffProcessPlans(source, current)` output for human-readable added/removed/changed process content. Revision detail now loads the source/previous immutable revision with request fencing and uses a dedicated full snapshot view covering step instructions/remarks, material provenance/IDs/codes/roles/weights/states, output flags/remarks, legacy inputs/outputs, all KCP/measurement/deviation/retest/confirmation fields, recipe totals, and step/major/final yields.
- Formal revision submission now loads the source snapshot, displays the version differences, requires an independent difference acknowledgement and change reason for revisions, and binds an accepted submission check to the current `{formId, versionNo}`. Open/form/version changes and failures invalidate prior checks and confirmations; cross-form reasons are cleared.
- Split artifact list and mutation request generations. Formula/SOP generation is globally mutually exclusive, route/revision changes invalidate both lanes, and every current generation success or failure awaits a fresh authoritative artifact list before releasing the UI.
- Added a Vite-only real-component harness and Playwright routes with deferred responses for batched server hydration, dirty cache precedence, stale submit reopen/version/form binding, flow confirmation cancel/commit, list-vs-generate, generate mutual exclusion, and failed-generation refresh.

### Round-5 verification

- Frontend Node suite: 75 passed, 0 failed.
- Shared, PC, and mobile type checks passed.
- PC and mobile Vite production builds passed; PC retained only the existing large-chunk warning.
- Frontend API-contract check and route check passed (`30` PC routes, `16` mobile routes).
- Backend `ProcessPlanControllerTest` and `ProcessSubmissionValidatorTest` passed against all 23 migrations.
- `git diff --check` passed.
- A real Chrome/Playwright run executed the four initial harness cases and exposed the Vue-proxy `DataCloneError`, mock response-code mismatch, and Ant button accessible-name mismatch. Those causes were fixed and the suite was expanded to five cases. A fresh browser rerun was attempted, but the platform rejected the required Chrome escalation because the execution-usage limit had been reached; therefore this report does **not** claim a passing Playwright rerun. The exact retained command is `./node_modules/.bin/playwright test e2e/pc-process-workspace.spec.ts --project=pc-chromium --workers=1` from `frontend/`.

## Exceptional remediation — parent integration closure

- Replaced the remaining parent `structuredClone(processPlan.value)` call with the shared Vue-aware clone boundary. The boundary now recursively unwraps nested proxies before its single structured-clone operation; the process workspace, flow repair, autosave coordinator, current major/minor editors, and legacy hierarchy editor all use that boundary, leaving no direct structured clone of process state.
- Added a real `ExperimentFormView` harness backed by Pinia and Vue Router. Its browser scenario loads task A from intercepted detail/process APIs, edits the real summary field, waits for the real local-cache timer and draft POST, asserts no Vue/harness `DataCloneError`, then navigates the same mounted parent to task B and verifies the B form and server process graph replace A.
- Reworked `diffProcessPlans` around semantic anchors plus stable sequence fallback instead of transport IDs. Nested source references are resolved to semantic major/step/material/output locations before comparison, so completely regenerated major/step/material/output/KCP/measurement IDs produce no differences while a real nested measurement change produces one precise human-readable change.
- Unified every major/minor mutation behind transactional flow repair. Any repair result, including one discovered by an ordinary text/control edit or stale external source cleanup, lists affected consumer names and requires confirmation; cancellation restores the prop-owned clone and emits nothing. The prior `disruptive` warning-and-commit bypass no longer exists.

### Exceptional-remediation verification

- Frontend Node suite: 79 passed, 0 failed.
- Shared, PC, and mobile direct type checks passed.
- PC and mobile Vite production builds passed; PC retained only the existing large-chunk warning.
- Frontend API-contract and route checks passed (`30` PC routes, `16` mobile routes).
- Backend focused `ProcessPlanControllerTest,ProcessSubmissionValidatorTest` passed against all 23 migrations.
- Playwright discovery passed and lists 7 PC workspace cases, including the real parent A→B/autosave case and ordinary-edit flow confirmation case. In accordance with the retained platform usage-limit restriction, Chrome was not launched and this report does **not** claim a fresh browser execution.
- `git diff --check` passed. Direct installed binaries were used because the pnpm wrapper attempted a blocked online metadata/dependency refresh in this linked worktree.

## Final integration-test closure

- Replaced the harness-only wildcard route with a shared named `process-workspace-experiment` route at `/rnd/tasks/:id/experiment`, retaining a named fallback for non-parent harness scenarios. The real `ExperimentFormView` now receives `route.params.id === "task-a"` before mount and its existing ID watcher observes `task-b` on same-instance navigation.
- Added an executable Node + Vue Router lifecycle probe that uses the exact router factory used by the browser harness. It proves the initial A load, A-specific draft-cache key and autosave binding, the A cache write before switching, then the B load, B-specific cache key and autosave binding.
- Tightened the real-parent Playwright interception so missing or unknown task/form IDs return 404 instead of silently falling back to A; the case explicitly asserts request order `task-a → task-b` and `form-a → form-b`.

### Final integration-test verification

- Frontend Node suite: 80 passed, 0 failed.
- PC direct type check and Vite production build passed; the build retained only the existing large-chunk warning.
- Frontend API-contract and route checks passed (`30` PC routes, `16` mobile routes).
- Playwright static discovery lists all 7 PC workspace cases. Chrome was not launched, in accordance with the retained usage-limit restriction.
- `git diff --check` passed.
