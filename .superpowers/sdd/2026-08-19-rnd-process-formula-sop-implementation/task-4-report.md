# Task 4 report — draft persistence and submission check

## Scope and audit

- Baseline audited: `b19b1b402ad74e2d8f5a8da1ffc55f3f712ebcfc`.
- Task 2 had already implemented the required `ProcessPlanService` persistence and load paths for step outputs, control points, measurements, material source fields, and `balanceToleranceKg`.
- The audited transaction saves a preceding step's outputs before a later step's `STEP_OUTPUT` material, and removes referencing materials before cascaded output deletion on replacement. No service production change was required or made in Task 4.

## TDD record

- RED: added `ProcessPlanControllerTest`, then ran `mvn -q -Dtest=ProcessPlanControllerTest test`. The submission-check request resolved as a missing resource and returned HTTP 500; the test expected HTTP 200. This proved the route was absent.
- GREEN: added `GET /api/v1/experiment-forms/{formId}/process-plan/submission-check`, which validates the saved draft via the authoritative `ProcessSubmissionValidator`.
- Regression coverage: added a real `ProcessPlanService` round trip containing an intermediate output, cross-step `STEP_OUTPUT` material reference, critical control point, three measurements, and non-default balance tolerance. Added MockMvc coverage for legacy JSON omitting `outputs` and `controlPoints`, which returns both as empty arrays, and for stable submission issue codes.

## Verification

- `mvn -q -Dtest=ProcessPlanServiceTest,ProcessPlanControllerTest,ProcessSubmissionValidatorTest test` — PASS (17 tests; 0 failures/errors).
- `git diff --check` — PASS.

## Self-review and follow-up focus

- Confirmed the endpoint runs validation on `service.find(formId)`, rather than accepting client-provided validation state.
- Confirmed controller tests use the real service, persistence layer, and validator; no validator mock can mask incorrect data flow.
- The new service test preserves an explicit output ID before it is used by the consuming step, exercising the foreign-key insertion sequence and draft replacement behavior already established by Task 2.
- Existing untracked `frontend/**/node_modules` symlinks were intentionally excluded from the task commit.
