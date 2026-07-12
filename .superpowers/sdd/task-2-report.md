# Task 2 Report: 实验单核价数据与产品负责人模型

## Status

Completed. V11 adds persisted product-owner and finished-output fields. The assignment API accepts an optional `productOwnerName`; blank or omitted values fall back to `assigneeName`. Experiment-draft output units fall back to `袋` when omitted.

## Red Evidence

Added `savesFinishedQuantityAndProductOwner` before production changes. It assigns `assigneeName="李研发"` with `productOwnerName="张研发"`, saves a draft with `finishedOutputWeightKg=8.5`, `finishedOutputQuantity=17`, and `finishedOutputUnit="袋"`, clears the experiment-form cache, and re-reads task detail.

Command:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest#savesFinishedQuantityAndProductOwner test
```

Observed RED result before implementation: `No value at JSON path "$.data.task.productOwnerName"`.

## Green Evidence

The same focused controller test passed after implementation:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest#savesFinishedQuantityAndProductOwner test
```

Final verification passed:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SchemaMigrationTest,SampleWorkflowControllerTest test
# Tests run: 58, Failures: 0, Errors: 0, Skipped: 0

cd frontend
pnpm --dir packages/shared typecheck
# tsc --noEmit
```

The migration test confirmed Flyway applied V11. H2 exposes the Chinese default unit in `information_schema` as `U&'\\888b'`, which the schema assertion checks directly.

## Changed Files

- `backend/src/main/resources/db/migration/V11__extend_experiment_output_and_product_owner.sql`
- `backend/src/main/java/com/lhr/rnd/api/AssignRndTaskRequest.java`
- `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java`
- `backend/src/main/java/com/lhr/rnd/api/SaveExperimentDraftRequest.java`
- `backend/src/main/java/com/lhr/rnd/model/ExperimentForm.java`
- `backend/src/main/java/com/lhr/rnd/model/RndTask.java`
- `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentFormEntity.java`
- `backend/src/main/java/com/lhr/rnd/persistence/entity/RndTaskEntity.java`
- `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`
- `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- `frontend/packages/shared/src/types/index.ts`
- `frontend/packages/shared/src/api/task.ts`
- `.superpowers/sdd/task-2-report.md`

## Commit

Task 2 implementation: `9686100 feat: persist experiment output and product owner`.

## Self-Check

- V11 backfills existing task product owners from `assignee_name`.
- The three new values are mapped through API request, domain models, JPA entities, persistence, hydration, and shared TypeScript contracts.
- Existing Java callers retain the three-argument `assignTask` overload and therefore default product owner to assignee.
- Existing frontend callers retain the original three-argument `assign` signature; the optional product-owner argument is appended.
- No H5 login or Feishu close-flow files were edited.

## Concern

No blocking concern. The schema-default assertion intentionally uses H2's Unicode literal representation, while the migration SQL and API value remain `袋`.
