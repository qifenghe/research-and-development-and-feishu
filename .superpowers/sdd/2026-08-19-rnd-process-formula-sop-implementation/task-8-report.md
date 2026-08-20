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
