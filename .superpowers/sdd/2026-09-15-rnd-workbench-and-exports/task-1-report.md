# Task 1 report: trial persistence and safe copy

## Outcome

Implemented independent, versioned trial drafts as JSON documents associated with an experiment form. The feature does not create a trial during GET and does not read or mutate the normalized active formal process graph.

Core files:

- `backend/src/main/java/com/lhr/rnd/model/TrialScheme.java`
- `backend/src/main/java/com/lhr/rnd/domain/TrialSchemeCopyService.java`
- `backend/src/main/java/com/lhr/rnd/service/TrialSchemeService.java`
- `backend/src/main/java/com/lhr/rnd/api/TrialSchemeController.java`
- `backend/src/main/resources/db/migration/V25__create_trial_scheme.sql`
- `backend/src/test/java/com/lhr/rnd/service/TrialSchemeServiceTest.java`
- `backend/src/test/java/com/lhr/rnd/domain/TrialSchemeCopyServiceTest.java`
- targeted additions to `RolePermissionService` for clean-database default permissions

## Exact HTTP contract

All responses except framework-level failures use `ApiResponse<T>`. Every endpoint requires the server-derived `SessionPrincipal`; no operator/audit identity is accepted from a request body. Only the assigned `RND_ENGINEER` and `RND_DIRECTOR` are authorized. `TESTER`, `QA_TESTER`, finance and other roles receive no trial routes.

### `GET /api/v1/experiment-forms/{formId}/trials`

- Body: none
- Response data: `TrialScheme[]`
- Includes archived and active trials, ordered active first and then most recently updated.
- Empty list is returned without auto-creating a trial.

### `POST /api/v1/experiment-forms/{formId}/trials`

Request:

```json
{
  "name": "方案A",
  "purpose": "string|null",
  "variables": "string|null",
  "plan": "ProcessPlan (required)",
  "plannedData": "PlannedData|null"
}
```

Response data: `TrialScheme`. The server sets trial version `1`, conclusion `PENDING`, source/archived/evaluation/inheritance/audit fields, remaps every supplied graph ID to a new server ID, clears untrusted control confirmation/retest-release fields, and sets embedded trial-plan version to `0` because optimistic locking is owned by the enclosing trial.

### `GET /api/v1/experiment-forms/{formId}/trials/{trialId}`

- Body: none
- Response data: `TrialScheme`

### `PUT /api/v1/experiment-forms/{formId}/trials/{trialId}`

Request:

```json
{
  "versionNo": 1,
  "name": "方案A",
  "purpose": "string|null",
  "variables": "string|null",
  "conclusion": "PENDING|ADJUST|REJECT|RECOMMEND",
  "recommendationReason": "string|null",
  "qualityScore": "number 0..10|null",
  "qualityNotes": "string|null",
  "difficulty": "EASY|MEDIUM|HARD|null",
  "plan": "ProcessPlan (required)",
  "plannedData": "PlannedData|null"
}
```

Response data: updated `TrialScheme`. Update is atomic with `where version_no=?`; stale writes fail with `TRIAL_VERSION_CONFLICT`. Trial/form/source/archive/inheritance/origin/audit fields are not request fields. Existing graph IDs remain stable; new nodes with null or client-local IDs receive server IDs and the response must replace the client copy. References and planned-data keys are remapped with those IDs. The embedded `ProcessPlan.id`, `experimentFormId`, `versionNo`, `status`, revision source and change reason are server-owned/normalized.

For a newly added major/step/material that already has a UI `key`, Task 3 may send that value as its temporary `id` and use the same value as the corresponding `plannedData` key. The service remaps both consistently and returns the durable ID. A planned key that does not name a node in the submitted graph is rejected; it is never silently orphaned.

### `POST /api/v1/experiment-forms/{formId}/trials/{trialId}/copy`

Request:

```json
{
  "versionNo": 1,
  "name": "方案B",
  "includeActuals": false
}
```

Response data: newly created `TrialScheme`, version `1`, with `sourceTrialId` set to the source.

### `POST /api/v1/experiment-forms/{formId}/trials/{trialId}/archive`

Request:

```json
{
  "versionNo": 1,
  "archived": true
}
```

Set `archived:false` to restore. Response data is the updated `TrialScheme`; the transition increments the optimistic version and uses `where version_no=?`.

## Response model

`TrialScheme` fields:

```text
id, experimentFormId, versionNo, name, sourceTrialId, archived,
purpose, variables, conclusion, recommendationReason, qualityScore,
qualityNotes, difficulty, plan, plannedData, inheritedActuals,
majorOrigins, createdBy, createdAt, updatedBy, updatedAt
```

`majorOrigins` is server-owned lineage (`currentMajorId -> originalMajorId`). It is initialized to self for newly created trials and retained transitively over copies so comparison does not match repeated same-name processes by name or row number.

`PlannedData`:

```text
materialWeightsKg: Map<materialId, BigDecimal>
stepParameters: Map<stepId, {parameter1Value, parameter2Value}>
majorYieldTargets: Map<majorId, BigDecimal>
batchYieldTarget: BigDecimal|null
yieldBasisNote: String|null
```

Map keys must identify the matching node in the same submitted trial graph. Step-material `sourceStepOutputId` must identify a step output in that graph, and process-input `sourceStepMaterialId` must identify a step material in that graph. Duplicate/dangling/cross-graph identifiers fail with `TRIAL_GRAPH_REFERENCE_INVALID`.

## Copy and actual-measurement safety

Default copy (`includeActuals:false`):

- allocates new IDs for plan, majors, steps, materials, step outputs, control points, measurements, process inputs and process outputs;
- remaps all internal flow references, planned-data keys and major lineage;
- moves actual step-material weights, step parameter values, major actual yields and batch actual yield into *missing* planned targets before clearing actuals;
- preserves explicit planned targets when present;
- clears actual material/output/input/process-output weights, step parameter values, measurements and calculated yields;
- preserves structure, instructions, equipment and control standards.

Copy with actuals (`includeActuals:true`):

- retains actual weights, parameters, measurements and calculated yields;
- sets `inheritedActuals:true` permanently for that copied trial;
- clears `resolved`, `confirmedBy`, `confirmedAt` and measurement `retestResult`; deviation-action observations remain part of the copied record;
- records newly mapped inherited measurement IDs in the server-only immutable `inherited_measurement_ids_json` column;
- also records immutable SHA-256 observation fingerprints in `inherited_measurement_fingerprints_json`. A fingerprint covers canonical measured value and measured time: the physical observation identity. It deliberately excludes the mutable/client-rekeyable ID plus interpreted result, confirmation, retest, deviation-action and remark metadata. Changing only `FAIL`/`PASS` therefore cannot make a copied observation appear newly measured.

Ordinary create/save also drops all client-supplied global control confirmation identity and retest release results. The public save request has no inheritance, origin, source or audit fields. `TrialSchemeService.inheritedMeasurementIds(formId, trialId, principal)` is the authorized server-side handoff for Task 2; the immutable stored ID set is not derived from the client `inheritedActuals` flag and is not cleared by later ordinary saves.

For the stronger ID-independent handoff, Task 2 should use the authorized `inheritedMeasurementFingerprints(formId, trialId, principal)` or `classifyInheritedMeasurements(formId, trialId, plan, principal)` methods. The classifier returns `currentMeasurementId -> inherited` by comparing physical observation content to the immutable copy-time fingerprint set. Re-keying an unchanged measurement or changing only its interpreted result remains inherited, while changing measured value/time makes it distinguishable. Neither fingerprints nor classifications are accepted from public create/save requests, and saves never update the stored copy-time set.

Task 2 consideration: a trial has no normalized control-point row, so it must not call the existing `/process-plan/control-points/{id}/confirm-deviation` endpoint. Add a dedicated, versioned trial confirmation operation that updates the JSON under lock and derives the confirmer from the session. If subsequent general saves should preserve that trusted confirmation, extend the save sanitizer to carry it only when major/step/control location, control definition and measurement observations are semantically unchanged. Promotion must treat every measurement classified by the immutable observation fingerprints as inherited even if its ID was re-keyed; the ID set remains useful as audit metadata but is not a complete security boundary.

## Lifecycle and permissions

- Reads require the existing task owner check (or R&D director) but remain available after the form is locked for historical viewing.
- Create/save/copy/archive first lock the form and require `experiment_form.status='DRAFT'`; `SUBMITTED_FOR_TEST` and `LOCKED` fail with `TRIAL_FORM_LOCKED`.
- Missing forms fail with `EXPERIMENT_FORM_NOT_FOUND`, including director requests.
- The migration and clean-database defaults add only these exact paths for engineers/directors: GET list, GET detail, POST create, PUT detail, POST copy and POST archive. No broad trial wildcard grants future promotion/confirmation paths.

## TDD and verification

Observed RED before production implementation:

```text
mvn -q -Dtest=TrialSchemeServiceTest,TrialSchemeCopyServiceTest test
COMPILATION ERROR: TrialScheme, TrialSchemeService and TrialSchemeCopyService not found
```

Focused final run:

```text
mvn -q -Dtest=TrialSchemeServiceTest,TrialSchemeCopyServiceTest test
14 tests, 0 failures, 0 errors, 0 skipped
```

Full final run:

```text
mvn -q test
323 tests, 0 failures, 0 errors, 0 skipped
```

The original 309-test baseline therefore gains 14 tests. Tests cover independent persistence, optimistic conflicts, server IDs, reference validation, malformed quality score, confirmation spoofing, assigned-owner/director authorization, forbidden roles and unassigned engineers, all locked-form mutations, missing form behavior, reversible archive, exact interceptor permissions, per-field default-copy merging, independent/transitive major lineage and immutable ID-independent inherited-measurement provenance.

### Review fix round 1 RED/GREEN evidence

The three review regressions were added before their implementations. The first focused RED stopped in test compilation because the new fingerprint accessors did not yet exist. After adding those method signatures, the two behavior regressions were also mutation-checked against the old implementations:

```text
mvn -q '-Dtest=TrialSchemeServiceTest#initializesIndependentServerMajorOriginsForRepeatedClientKeysAndCopiesThemTransitively,TrialSchemeCopyServiceTest#defaultCopyMergesStepParameterPlansPerField' test
2 tests, 2 failures
- independent create retained MAJOR-SHARED-KEY instead of durableMajorId -> itself
- planned ('25', null) remained ('25', null) instead of merging actual parameter2 '95'
```

After restoring the fixes, the complete focused suite passed 14/14. The added service test also demonstrates that an unchanged copied measurement remains classified inherited after a client ID re-key/save, a result-only `PASS`/`FAIL` reinterpretation remains inherited, a changed measured value is not classified inherited, and the immutable stored fingerprint set does not change across saves. The result-only assertion was independently observed RED while `result` was still part of the fingerprint, then GREEN after narrowing the fingerprint to measured value plus measured time.

## Self-review notes

- No trial endpoint calls the active formal process save or confirmation endpoint.
- Controller request records omit trial IDs, source, archived/inherited flags, major origins and audit fields; nested plan metadata is normalized server-side.
- Copy source and save/archive targets are selected by both `trialId` and `formId`; source/save/archive rows are locked and versions checked.
- JSON map keys and internal material/output references are validated against the submitted graph before persistence.
- Existing dirty frontend and unrelated files were not edited or staged.
- Code-review subagent dispatch was intentionally skipped because the task explicitly required no subagents; the scoped diff and security boundary were reviewed locally instead.
