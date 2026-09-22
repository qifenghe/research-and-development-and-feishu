# Task 6 report — 2026-09-22

Status: DONE_WITH_CONCERNS. API/regression/check deliverables are complete; controller found an unresolved real UI missing-weight summary issue and is conducting final CUA/independent review. No production code changed, no subagents, branch/reset/push/deploy or unrelated edits.

## Implemented

- `scripts/verify-rnd-trials.mjs`: local-only real-session acceptance, 149 named assertion groups. Each run creates independently labelled software fixtures; fixed preview URL, configured bootstrap password supplied ephemerally through environment, no token/password output or persisted credentials, no notifications, Feishu disabled.
- A/B saves/reload, planned/actual/null, optimistic stale save/confirm/export, archive/restore, inherited measurement rejection, trusted critical confirmation, version-bound explicit promotion, idempotence, populated displaced draft recovery, saved-source read-only previews, new formal SOP/packaging eligibility rejection, immutable formal snapshots/artifact bytes, exact workbook sums and source/version labels, no monetary fields, pinned tester authorization, finance/draft restriction, sent/locked lifecycle.
- Strengthened existing early-failure rollback test with a populated two-step/internal-reference graph; added final-promotion-INSERT failure after revision creation. Real test DB constraint fault; full graph/state/revisions/promotion/audit/trial rollback and successful retry/displacement asserted.
- User-facing report: `docs/研发工作台与导出验收-2026-09-15.md`, with exact commands/results, role/behavior matrix, artifacts and visual-QA evidence, runtime and remaining concerns.

## Verification / TDD evidence

No production implementation/fix was necessary; tests target existing transactional behavior. Initial regression failures were fixture setup errors (internal STEP_OUTPUT incorrectly retained external material code; fixed IDs reused across test methods). Fixed fixture only and reused existing real remapper. Both populated-graph regressions pass; no validator loosened. The new final-insert fault proves rollback of already-created revision/audit, not just an early validation rejection.

API initial runs similarly exposed test harness mismatches, not production defects: shared export stale code is `PROCESS_EXPORT_VERSION_CONFLICT`; legacy pricing download MIME is `application/octet-stream`; critical control DTO fields are `controlType`/`importance`/`measurementTool`/`deviationAction`; NONE zero legacy observation fixture must not masquerade as invalid layered zero-input flow; XLSX totals use sparse cell coordinates. Corrected harness to actual documented/source contracts and independently literal expected totals. Final script passes against latest built runtime.

Final serial per-package verification, 2026-09-22:

```text
backend: mvn -q -Dtest=TrialPromotionServiceTest test
16 tests; failures=0 errors=0 skipped=0
/private/tmp/task6-rollback-tests.log

backend: mvn -q test
Surefire XML sum: 360 tests; failures=0 errors=0 skipped=0
/private/tmp/task6-backend-full.log

frontend: node --experimental-strip-types --test tests/*.test.mts
155 tests; pass=155 fail=0 cancelled/skipped/todo=0
/private/tmp/task6-frontend-full.log

frontend: pnpm check:api-contracts; pnpm check:routes; pnpm typecheck; pnpm build
All exit 0; PC routes=30 mobile routes=16; shared/PC/mobile types and both builds pass.
/private/tmp/task6-{contracts,routes,typecheck,frontend-build}.log

root: node scripts/run-rnd-preview.mjs stop / build / start / status
Latest production code base12161a9 built; only dedicated 8082/5183/5184 touched.
Final three health checks 200. /private/tmp/task6-preview-build.log

root: node scripts/verify-rnd-trials.mjs (RND_PREVIEW_PASSWORD from configured bootstrap, memory-only)
149/149, exit0. /private/tmp/task6-api.log
root: node --check scripts/verify-rnd-trials.mjs; git diff --check
exit0
```

Baseline warnings remain: Flyway H2/JVM/native/Mockito, mounted Vue harness Router warnings and Vite chunk advisory; expected fault-injection logging is not test failure. No warning suppression/dependency change. Full backend and frontend groups ran concurrently with each other but never overlapping another Maven/build within their own package; focused Maven had finished first.

## Actual API artifacts

`data/preview/Task6-2026-09-22T11-09-56-390Z/results.json` and sibling downloaded files:

- TASK-0016 / EXP-0016 / VER-0016; formal R2 / A V4.
- Revision `PREV-4edf6dc4-5529-416a-9477-022692ed39e6`; pricing `PRICE-0008` remains PENDING_PRICING_REVIEW.
- Formula `PART-6cbf9070-9891-414a-a58f-1d3c61c7e9b4`, SHA-256 `5e50be649739b3d3f323de8d09235e5dd8b9b7d0a27c918216b954ee0ddbc4da`.
- SOP `PART-f84db33b-ac2f-4f9c-9755-cbed031f47e5`, SHA-256 `ebb43e6720057e682a2ad1fd6040ae0c4d48a25db9f709d738e0242bc7f8b812`.
- Formula 102 actual / 100 normalized; pricing 102 external / 72% primary / independent74kg+74袋. Three files share exact immutable source label and have no monetary fields. Old source/artifact tester requests denied; pinned ones pass.

Earlier debug fixtures TASK-0010 through TASK-0015 preserved and marked software-test-only, no business records reused. Controller owns UI interaction on TASK-0010. Only generated local evidence files are untracked; they are intentionally excluded from commit.

## Artifact visual QA

No renderer changes, so do not duplicate controller's accepted render runs. Task4 pricing final 12 pages at `/private/tmp/rnd-export-qa-0922.8zDblM/{formal-v2,print-v2}`; Task5 latest SOP v4 **4 pages** at `sop-v4`; formula v4 **2 sheets / 6 pages** at `formula-v4`. Original application artifacts remain in `backend/target/task-{4,5}-export-qa`. User report lists exact fixture names and evidence distinctions. All food/process fixture values are software examples, not validated production standards.

## Self-review / remaining concerns

- No production diff. Scoped test assertions inspect real graph and complete audit snapshot, no mock-only behavior. API assertions inspect real downloaded OOXML values by column coordinates, real source/revision responses, actual role sessions and before/after state. No monetary or credentials output.
- Browser state changed after original policy block: controller can now normally create authorized local tab and use CUA; no bypass. Controller reported TASK-0010 B's missing actual summary cards still show `0.000kg` even while yield says pending. Record as unresolved UI defect, do not claim all-green UI or fix it outside this task. Controller will append remaining CUA/download findings and perform final independent whole-plan review.
- Controller's final CUA pass: B purpose save-and-switch persisted as V5 and remained after reload while A stayed independent; unsaved-choice dialog, missing-vs-zero A/B comparison, saved-only export preview gating and accurate three-type missing checks passed. Formal-submit modal showed server checks/balance warnings/change reason/explicit checkbox, disabled confirmation until checked; canceled with no extra promotion. Input DOM values are genuinely empty (AX text alone misread them as zero). Two real missing-to-zero summaries remain (major weight/balance cards and step external-material summary). Three long test scheme names also cause horizontal page overflow at approximately1091px. Formula download click showed no page error, but the CUA download-event wait timed out after3s; browser file receipt is unconfirmed, API download is separately proven. User report contains all these distinctions.
- Only one real bootstrap tester session available; unassigned/archived/extra-role boundaries also covered by integration tests, not claimed as an additional real-session matrix.
- Preserved unrelated `DemandDetailView.vue`, plan/review edits, untracked prior `verify-rnd-roles.mjs`, reports/cache/outputs. Commit contains only four task-scoped files.

Commit: the scoped `test: verify trial promotion roles and immutable exports` commit containing this report (SHA returned in final handoff; no extra commit needed to write its own hash).

## Review fix round 1 — 2026-09-22

Base `1eea0f8`. Scoped acceptance-only change; no production code, new entity/interface, subagent, runtime restart, notification, unrelated-file edit or package-wide rerun.

1. Expanded both formal-export forbidden-field assertions to `单价|金额|成本|毛利|税率|税费|利润|自动报价`: the first assertion covers real FORMULA_XLSX and SOP_DOCX downloads, the second covers real PRICING_XLSX. Updated the user report matrix with the exact binding terms.
2. Investigated the supposed material-master read contract: current controllers/entities/repositories/migrations have experiment-owned `experiment_material`, process-owned `experiment_step_material`/outputs and packaging template rows, but no material-master/library entity or API. `MATERIAL_CATEGORY` is a classification dictionary, not a master list. V23's persisted STEP_OUTPUT consistency rule requires internal output ID and null material/formula IDs. Reviewer independently confirmed the absence and withdrew that Important finding. No interface or schema was invented.
3. With controller approval, retained useful bounded assertions against existing contracts: a run-unique intermediate output name survives in the formal snapshot; the downstream STEP_OUTPUT points to its exact output ID with null `materialCode`/`formulaMaterialId`. After the entire trial/promotion/test/export/pricing workflow, a real researcher GET `/rnd-tasks/{id}/detail` returns `currentExperimentForm.materials` deeply equal to the initial saved form's external materials, with no intermediate named entry. This verifies external-list integrity, **not** a non-existent master API. Existing independently expected 102kg export sum remains the no-double-count proof.

Verification (one acceptance run, existing configured bootstrap secret read locally and supplied only through child-process environment; no secret logging):

```text
node --check scripts/verify-rnd-trials.mjs
exit 0

RND_PREVIEW_PASSWORD=<ephemeral configured local value> node scripts/verify-rnd-trials.mjs
exit 0; RESULT 152/152
PASS intermediate stays an internal output reference without material-master identity
PASS complete trial/promotion/export workflow leaves experiment external-material records unchanged
log: /private/tmp/task6-fix1-api.log
evidence: data/preview/Task6-2026-09-22T11-22-00-243Z/results.json
software fixture: TASK-0017 / EXP-0017; pricing PRICE-0009

git diff --check
exit 0
```

No production fix/RED cycle required: these are additional acceptance assertions for existing behavior and were green on the single requested run. Prior 360 backend / 155 frontend full-suite evidence remains from the original Task6 turn, not claimed as rerun here. No renderer change or repeat QA. Browser's second download-event wait (15s) also timed out with no console errors: file receipt remains unconfirmed; final UI missing-to-zero/long-name overflow findings remain for the controller's review/fix wave.

Changed only `scripts/verify-rnd-trials.mjs`, this report, and the corresponding user-facing acceptance report.
