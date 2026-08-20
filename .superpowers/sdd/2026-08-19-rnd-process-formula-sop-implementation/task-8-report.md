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
