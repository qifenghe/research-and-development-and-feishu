# Task 2 report: transactional trial promotion and trusted control evidence

## Outcome and scope

Implemented the Task 2 backend on the existing `codex/rnd-sample-foundation` branch, starting from Task 1 commit `ced2f26`. No frontend, monetary fields, worktree changes, or unrelated dirty files were included. The submitted `ProcessRevision` model and its existing immutable `snapshot_json` format remain unchanged.

New focused components:

- `TrialPromotionService`: serialized preview/submission, immutable promotion provenance, idempotency and authorized recovery/source reads.
- `TrialControlEvidenceService` (package-private): confirmation validation, trusted evidence preservation/transfer, and semantic graph comparison.
- `TrialPromotionSourceController`: separates formal-source reads from private trial reads.
- `V26__bind_trial_revision_provenance.sql`: preview and promotion records plus exact route permissions.
- `TrialPromotionServiceTest`: 15 integration tests using real JDBC, Flyway, trial/formal services and transactions.

Targeted existing code changes:

- `TrialSchemeController`: four explicit versioned confirmation/preview/submit endpoints.
- `TrialSchemeService`: versioned confirmation commands, trusted evidence preservation on unchanged saves, confirmation audit records.
- `ProcessPlanService`: package-private `savePromotedTrial(formId, plan)` storage path. It delegates to the same existing graph persistence but preserves freshly server-remapped step-material IDs, so `ProcessInput.sourceStepMaterialId` remains attached to the correct persisted material. Existing normal/public saves retain their previous sanitization and ID allocation behavior; there is no public identity-bypass route.
- `RolePermissionService`: clean-database defaults matching the exact V26 endpoint grants.

## Exact HTTP contract

Every endpoint below returns `ApiResponse<T>`. All identity comes from the existing server `SessionPrincipal`; request bodies have no actor, confirmer, or audit fields.

### POST `/api/v1/experiment-forms/{formId}/trials/{trialId}/control-points/{pointId}/confirm`

Request:

```json
{"trialVersionNo": 1}
```

Response data: updated `TrialScheme`, with `versionNo` incremented by one and server `confirmedBy`/`confirmedAt` on the point.

Allowed: assigned R&D engineer or R&D director, only while experiment form is `DRAFT` and trial is not archived. All measurements must have measured value, ISO local date-time, and PASS/FAIL interpretation; passing confirmation requires every measurement PASS and within configured limits. Reversed limits fail. Missing values/times/pending results and any deviation cannot be pass-confirmed.

### POST `/api/v1/experiment-forms/{formId}/trials/{trialId}/control-points/{pointId}/confirm-deviation`

Request:

```json
{"trialVersionNo": 2, "resolutionNote": "延长熟制后复测通过"}
```

Response data: updated `TrialScheme`, version incremented. Director only. Nonblank resolution note (maximum 2000 characters), a documented `deviationAction` for every failed/out-of-limit observation, and an independent in-range PASS measurement **strictly later than the last failed observation** are required. The server sets `resolved`, confirmer/time, and PASS retest-release metadata on failed observations. Request retest flags are not accepted as evidence.

The control's original `basisOrRemark` is preserved. The resolution note is recorded separately in the existing audit log, action `TRIAL_CONTROL_DEVIATION_CONFIRMED`, with immutable session user ID, form ID, point ID and resulting trial version. Passing confirmation uses `TRIAL_CONTROL_PASS_CONFIRMED`. Rejected confirmations do not add audit records.

### POST `/api/v1/experiment-forms/{formId}/trials/{trialId}/submission-preview`

Request:

```json
{"trialVersionNo": 3, "expectedProcessVersionNo": 4}
```

Response data:

```text
{
  trialVersionNo: number,
  expectedProcessVersionNo: number,
  checks: ProcessSubmissionCheck {ready, errors[], warnings[]},
  differingDraft: boolean,
  previewToken: string,
  previewHash: string
}
```

Allowed: assigned R&D engineer/director; form must remain DRAFT and trial active. Call after saving any trial changes or explicit confirmation, using the returned trial version and freshly loaded active process version. For a new form with no normalized process plan, `expectedProcessVersionNo` is `0` (the existing `ProcessPlanService.find` result).

`checks` comes from the existing formal submission validator, including the critical-control gate, physical flow, weights, and yield/balance rules. Planned yield targets and cached yield fields do not satisfy this gate.

`differingDraft` compares canonical actual graph content: IDs and internal references are consistently remapped, numeric scale and ISO timestamp formatting are normalized, and lifecycle/audit/release metadata plus derived `major.yield` and `batchYieldPercent` caches are excluded. A semantically identical graph does not report a difference just because its server IDs differ. A nonempty legacy draft is included; an empty initial legacy placeholder is not treated as a draft to displace. A currently SUBMITTED graph is an immutable prior revision, not a displaced active draft, so its flag is false.

`previewToken` is an opaque persisted, server-generated token bound to form, trial, both versions, user ID and the exact current snapshot hash. `previewHash` is the exact-snapshot guard, not the canonical graph equality key. It binds the complete persisted trial (including planned data/evaluation/audit) and active plan. A new preview is required if either input snapshot changes.

### POST `/api/v1/experiment-forms/{formId}/trials/{trialId}/submit`

Request:

```json
{
  "trialVersionNo": 3,
  "expectedProcessVersionNo": 4,
  "previewToken": "TPRE-...",
  "confirmed": true,
  "changeReason": null,
  "idempotencyKey": "client-generated-stable-key"
}
```

Response data: existing `ProcessRevision` (unchanged DTO), including `id`, `revisionNo`, `snapshotHash` and normalized formal `snapshot`.

`idempotencyKey` is required, maximum 120 characters. Reuse the **same complete body and key** on retry. The key is scoped to form and bound to trial ID, submitting user ID and all request payload fields. An exact retry returns the original revision ID even after the active process has become SUBMITTED; concurrent identical requests produce exactly one revision/promotion. Reusing a key with changed content fails. An already completed authorized retry may also return after the form is sent/locked; it makes no new write.

`confirmed:true` is always required, including when replacing a differing active draft. If the active process is SUBMITTED, nonblank `changeReason` (maximum 1000 characters) is required: the existing revision service creates a new draft from that current formal revision before the trial is transferred/submitted. The new formal revision keeps its `sourceRevisionId`; the old formal snapshot is unchanged. If replacing an existing formal-derived DRAFT, existing persisted change-reason conflict checks remain active; pass the persisted reason or null, not an override.

Submission does not edit, archive or increment the trial. It stores the exact submitted trial snapshot and version independently, so later trial edits cannot change provenance. Refresh the active formal process after success.

### GET `/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/source`

Response data is `null` for a non-trial-origin formal revision, otherwise:

```text
{
  promotionId: string,
  trialId: string,
  trialName: string,
  trialVersionNo: number,
  trialSnapshotHash: string,
  promotedBy: string,
  promotedAt: ISO local date-time string
}
```

This calls the existing `ProcessRevisionService.find(formId, revisionId, principal)` formal-read path first. The assigned researcher and R&D director can read it. TESTER/QA_TESTER can read it **only for the exact revision pinned to their active test assignment**; another revision, another tester, or an archived assignment is denied. Finance is not granted formal revision access by the existing policy and remains denied.

No trial plan, planned data, purpose/notes/evaluation, displaced snapshot, user ID, or full trial-snapshot JSON is exposed by this endpoint. Trial name/version are immutable submit-time metadata, not live lookups of the editable trial. Exports can call `TrialPromotionService.source(formId, revisionId, principal)` to obtain these public fields without gaining draft access. The existing revision DTO/snapshot stays backwards-compatible.

### GET `/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}/displaced-draft`

Response data: original saved `ProcessPlan`, or `null` if that promotion displaced no differing draft (including a non-trial revision).

This separately requires existing **draft-read authorization** and formal revision ownership. Only the assigned researcher/R&D director can retrieve the snapshot, including for historical forms. Testers and finance cannot retrieve it even if they can see source metadata. Task 3 may offer “copy recovered draft into a new trial” using this returned `ProcessPlan` and the existing trial create endpoint; GET itself does not restore or overwrite anything.

## Trust and transaction guarantees

1. Preview and submit acquire locks in form → normalized active process → trial order, then reload both snapshots under those locks. This also serializes a form that has no normalized process row yet.
2. Both optimistic versions must match. Submit also checks the persisted preview token/user/snapshot binding and explicit confirmation.
3. Trial save normalization still discards all incoming global confirmation and retest-release fields. Trusted prior evidence is carried forward only when point identity, major/step location, complete control definition and measurement observations are unchanged. Changes clear it. Spoofed confirmer values cannot replace a previously trusted confirmer.
4. Confirmation compares Task 1's immutable physical-observation fingerprints (value + canonical ISO local time), independently of IDs, interpretation/result or time formatting. Rekeying a copied measurement or rewriting PASS/FAIL does not make it newly observed. Unchanged inherited observations cannot be re-confirmed. Z/offset times remain unsupported, as in Task 1.
5. Promotion allocates a fresh graph through the Task 1 remapper, transfers only persisted server confirmation evidence, and preserves internal graph references. It never sends confirmation-request markers through the ordinary public save sanitizer.
6. Differing active draft JSON is captured before replacement. All graph writes, optional reopening of a submitted formal revision, normalized formal validation/submission, revision audit and promotion insert share one transaction. Validation/storage failure rolls back graph/status/revision/promotion changes; the previously completed preview remains available as a separate earlier operation.
7. Final submission is performed by the existing formal service and validator against persisted/recalculated actual data. No critical-control bypass, planned-yield substitution, recommendation-to-formal shortcut, or snapshot-format change was introduced.
8. V26 promotion rows store form/trial/revision FKs, immutable trial JSON + hash, optional displaced draft JSON, idempotency key + payload hash, preview token and trusted submit audit. There is no endpoint that updates historical promotion rows.

## Verification

Initial RED (before implementation):

```text
mvn -q -Dtest=TrialPromotionServiceTest test
Compilation failed: TrialPromotionService and versioned confirmation command/API symbols did not exist.
```

Additional behavioral REDs observed before their respective fixes:

- Exact promotion/confirmation/source permission assertions were false before the V26/default grants.
- Confirmation audit lookup found no row before trusted-ID audit recording was added.
- Deviation closure overwrote `basisOrRemark` with the disposition text; preservation assertion failed before removing that assignment.
- Nonempty legacy draft preview incorrectly returned `differingDraft:false`; regression failed before including legacy content.

Final focused run:

```text
mvn -q -Dtest=TrialPromotionServiceTest test
15 tests, 0 failures, 0 errors, 0 skipped
```

Final full backend run:

```text
mvn -q test
340 tests, 0 failures, 0 errors, 0 skipped
```

Baseline was 325; Task 2 adds 15 tests. Tests cover explicit/stale/unauthorized rejection, inherited measurement rekey/result/time-format defense, unchanged-save trusted evidence, invalidated definitions/observations/location, documented director retest release, confirmation identity audit, exact permissions, actual graph ID/reference consistency, planned/evaluation snapshot retention and actual-only calculated yield, DRAFT backup/recovery, legacy backup, SUBMITTED-to-next-formal transition, previous immutable snapshots, storage-failure rollback, exact sequential/concurrent retry and mismatch rejection, and pinned-tester metadata versus researcher-only draft recovery.

`git diff --check` passed. Existing Java native-access/Flyway H2 version warnings remain; there are no test failures. No new subagent was spawned (explicit task instruction); the scoped diff was self-reviewed, and the coordinator's control-basis review correction was applied with RED/GREEN evidence.

## Integration notes / limits

- Preview records are durable and not yet pruned; no cleanup/retention workflow was added in this task.
- Formal source metadata is a separate optional endpoint rather than an additive field in the existing revision JSON. Export integrations should read it by revision ID, never through a trial-draft lookup.
- Researcher recovery returns the complete original draft as a copy source; it deliberately does not auto-create a trial or restore the active graph.
- The process-plan internal trusted storage method is package-private and must remain unreachable from HTTP handlers.
- Existing normal formal-save behavior is intentionally unchanged outside the new promotion-only material-ID preservation path.
