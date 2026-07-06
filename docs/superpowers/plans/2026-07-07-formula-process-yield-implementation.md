# Formula, Process Loss, and Yield Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the experiment form around a structured formula, tabbed process loss records, residual-material tracking, and finished-product yield calculated from the unique primary raw material.

**Architecture:** Extend the existing experiment material, process, and form tables rather than introducing a parallel workflow. Put all weight, loss, and yield formulas in a small shared calculation layer, validate them again in Spring Boot when drafts are saved, and keep PC/mobile views as presentation adapters over the same API contract.

**Tech Stack:** Java 17, Spring Boot 3, JPA, Flyway, PostgreSQL/H2, Vue 3, TypeScript, Ant Design Vue, Vant, Node test runner, JUnit 5.

---

### Task 1: Add calculation rules with unit tests

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java`
- Test: `backend/src/test/java/com/lhr/rnd/domain/ExperimentCalculationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
class ExperimentCalculationServiceTest {
    private final ExperimentCalculationService service = new ExperimentCalculationService();

    @Test
    void calculatesProcessLossAfterRemovingResidualMaterial() {
        var result = service.processLoss(new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("0.5"));
        assertEquals(new BigDecimal("1.5000"), result.lossWeightKg());
        assertEquals(new BigDecimal("0.150000"), result.lossRate());
    }

    @Test
    void calculatesFinishedYieldFromPrimaryRawMaterial() {
        assertEquals(new BigDecimal("0.800000"),
                service.finishedYield(new BigDecimal("10"), new BigDecimal("12.5")));
    }

    @Test
    void rejectsWeightsWhoseOutputAndResidualExceedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.processLoss(new BigDecimal("10"), new BigDecimal("9"), new BigDecimal("2")));
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `cd backend && mvn -q -Dtest=ExperimentCalculationServiceTest test`

Expected: compilation failure because `ExperimentCalculationService` does not exist.

- [ ] **Step 3: Implement the calculation service**

```java
public final class ExperimentCalculationService {
    public ProcessLoss processLoss(BigDecimal input, BigDecimal output, BigDecimal residual) {
        var safeResidual = residual == null ? BigDecimal.ZERO : residual;
        var loss = input.subtract(output).subtract(safeResidual);
        if (loss.signum() < 0) throw new IllegalArgumentException("出成与余料不能超过投入重量");
        var rate = input.signum() == 0 ? null : loss.divide(input, 6, RoundingMode.HALF_UP);
        return new ProcessLoss(loss.setScale(4, RoundingMode.HALF_UP), rate);
    }

    public BigDecimal finishedYield(BigDecimal output, BigDecimal primaryInput) {
        if (primaryInput == null || primaryInput.signum() <= 0) return null;
        return output.divide(primaryInput, 6, RoundingMode.HALF_UP);
    }

    public record ProcessLoss(BigDecimal lossWeightKg, BigDecimal lossRate) {}
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run: `cd backend && mvn -q -Dtest=ExperimentCalculationServiceTest test`

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java backend/src/test/java/com/lhr/rnd/domain/ExperimentCalculationServiceTest.java
git commit -m "feat: add experiment weight calculations"
```

### Task 2: Extend the database schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__extend_experiment_formula_and_yield.sql`
- Modify: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`

- [ ] **Step 1: Add failing schema assertions**

Add assertions for these columns:

```java
assertColumnExists("EXPERIMENT_MATERIAL", "MATERIAL_CATEGORY");
assertColumnExists("EXPERIMENT_MATERIAL", "IS_PRIMARY_MATERIAL");
assertColumnExists("EXPERIMENT_MATERIAL", "FORMULA_RATIO");
assertColumnExists("EXPERIMENT_MATERIAL", "INPUT_UNIT");
assertColumnExists("EXPERIMENT_PROCESS", "REMAINING_WEIGHT_KG");
assertColumnExists("EXPERIMENT_PROCESS", "REMAINING_DISPOSITION");
assertColumnExists("EXPERIMENT_PROCESS", "LOSS_WEIGHT_KG");
assertColumnExists("EXPERIMENT_FORM", "FINISHED_OUTPUT_WEIGHT_KG");
assertColumnExists("EXPERIMENT_FORM", "FINISHED_YIELD_RATIO");
```

- [ ] **Step 2: Run migration test and verify RED**

Run: `cd backend && mvn -q -Dtest=SchemaMigrationTest test`

Expected: assertions fail for missing V9 columns.

- [ ] **Step 3: Create migration V9**

```sql
alter table experiment_material add column material_category varchar(30);
alter table experiment_material add column is_primary_material boolean not null default false;
alter table experiment_material add column formula_ratio numeric(10, 6);
alter table experiment_material add column input_unit varchar(10) not null default 'kg';

update experiment_material
set material_category = case
    when stage in ('辅料', 'AUXILIARY') then 'AUXILIARY'
    when stage in ('包材', 'PACKAGING') then 'PACKAGING'
    else 'RAW'
end;

alter table experiment_process add column remaining_weight_kg numeric(14, 4);
alter table experiment_process add column remaining_disposition varchar(30);
alter table experiment_process add column loss_weight_kg numeric(14, 4);

alter table experiment_form add column finished_output_weight_kg numeric(14, 4);
alter table experiment_form add column finished_yield_ratio numeric(10, 6);
```

- [ ] **Step 4: Run migration test and verify GREEN**

Run: `cd backend && mvn -q -Dtest=SchemaMigrationTest test`

Expected: migration applies through V9 and all assertions pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__extend_experiment_formula_and_yield.sql backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java
git commit -m "feat: extend experiment persistence schema"
```

### Task 3: Extend backend models and persistence mapping

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/model/ExperimentMaterial.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/ExperimentProcessStep.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/ExperimentForm.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentMaterialEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentProcessEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentFormEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

- [ ] **Step 1: Add a failing save-and-read controller test**

Save a draft containing one primary RAW material, one process with residual material, and finished output. Assert that the response contains:

```java
jsonPath("$.data.materials[0].materialCategory").value("RAW")
jsonPath("$.data.materials[0].primaryMaterial").value(true)
jsonPath("$.data.materials[0].utilizationRate").value(1.0)
jsonPath("$.data.processSteps[0].remainingWeightKg").value(0.5)
jsonPath("$.data.processSteps[0].lossWeightKg").value(1.5)
jsonPath("$.data.finishedOutputWeightKg").value(8.0)
jsonPath("$.data.finishedYieldRatio").value(0.8)
```

- [ ] **Step 2: Run controller test and verify RED**

Run: `cd backend && mvn -q -Dtest=SampleWorkflowControllerTest test`

Expected: JSON paths are missing.

- [ ] **Step 3: Extend the records**

Use these field shapes:

```java
public record ExperimentMaterial(
        String stage, int sequence, String materialCode, String materialName,
        BigDecimal weightKg, BigDecimal utilizationRate, String remark,
        String materialCategory, boolean primaryMaterial,
        BigDecimal formulaRatio, String inputUnit) {}

public record ExperimentProcessStep(
        int sequence, String processName, BigDecimal beforeWeightKg,
        BigDecimal afterWeightKg, BigDecimal remainingWeightKg,
        String remainingDisposition, BigDecimal lossWeightKg,
        BigDecimal lossRate, String remark) {}
```

Add `finishedOutputWeightKg` and `finishedYieldRatio` to `ExperimentForm` immediately before timestamps.

- [ ] **Step 4: Update entities, constructors, getters, hydration, and persistence**

Map every V9 column in all entity constructors and in both directions inside `SampleWorkflowService`. For old material rows, use:

```java
var utilization = material.utilizationRate() == null ? BigDecimal.ONE : material.utilizationRate();
var category = material.materialCategory() == null ? categoryFromStage(material.stage()) : material.materialCategory();
```

- [ ] **Step 5: Validate draft invariants before persistence**

In `saveExperimentDraft`, enforce:

```java
var primary = materials.stream().filter(ExperimentMaterial::primaryMaterial).toList();
if (primary.size() > 1) throw new BusinessException("PRIMARY_MATERIAL_DUPLICATED", "只能指定一个主原料");
if (primary.stream().anyMatch(m -> "PACKAGING".equals(m.materialCategory())))
    throw new BusinessException("PRIMARY_MATERIAL_INVALID", "包材不能设为主原料");
```

Recalculate formula ratios, process losses, and finished yield with `ExperimentCalculationService`; never persist unverified client calculations.

- [ ] **Step 6: Run backend tests and verify GREEN**

Run: `cd backend && mvn -q test`

Expected: all backend tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java
git commit -m "feat: persist structured formula and process losses"
```

### Task 4: Update the shared frontend contract and pure calculations

**Files:**
- Modify: `frontend/packages/shared/src/types/index.ts`
- Create: `frontend/packages/shared/src/experiment/calculations.ts`
- Modify: `frontend/packages/shared/src/index.ts`
- Test: `frontend/tests/experiment-calculations.test.mts`

- [ ] **Step 1: Write failing TypeScript tests**

```ts
test("calculates formula ratios from actual weights", () => {
  assert.deepEqual(formulaRatios([12.5, 2.5]), [83.333333, 16.666667]);
});

test("calculates process loss after residual material", () => {
  assert.deepEqual(processLoss(10, 8, 0.5), { lossWeightKg: 1.5, lossRate: 15 });
});

test("calculates yield from the primary raw material", () => {
  assert.equal(finishedYieldPercent(10, 12.5), 80);
});
```

- [ ] **Step 2: Run test and verify RED**

Run: `node --test frontend/tests/experiment-calculations.test.mts`

Expected: missing module/functions.

- [ ] **Step 3: Add types and calculations**

```ts
export type MaterialCategory = "RAW" | "AUXILIARY" | "PACKAGING";
export type RemainingDisposition = "REUSE" | "RETURN" | "DISCARD";

export function processLoss(input: number, output: number, residual = 0) {
  const lossWeightKg = input - output - residual;
  if (lossWeightKg < 0) throw new Error("出成与余料不能超过投入重量");
  return { lossWeightKg, lossRate: input > 0 ? lossWeightKg / input * 100 : 0 };
}
```

Extend shared interfaces with the V9 fields using optional properties for old records.

- [ ] **Step 4: Run tests and typecheck**

Run: `node --test frontend/tests/experiment-calculations.test.mts && pnpm --dir frontend typecheck`

Expected: all tests and all workspace typechecks pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/packages/shared frontend/tests/experiment-calculations.test.mts
git commit -m "feat: add shared experiment calculations"
```

### Task 5: Redesign the PC experiment form

**Files:**
- Create: `frontend/apps/pc/src/components/FormulaEditor.vue`
- Create: `frontend/apps/pc/src/components/ProcessTabsEditor.vue`
- Create: `frontend/apps/pc/src/components/FinishedYieldEditor.vue`
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`
- Test: `frontend/e2e/pc-experiment-form.spec.ts`

- [ ] **Step 1: Write a failing browser test**

The test must log in as `rnd_director`, open an editable experiment, and assert:

```ts
await expect(page.getByText("配方", { exact: true })).toBeVisible();
await expect(page.getByText("类别", { exact: true })).toBeVisible();
await expect(page.getByText("主原料", { exact: true })).toBeVisible();
await expect(page.getByText("利用率", { exact: true })).toBeVisible();
await expect(page.getByText("工序损耗", { exact: true })).toBeVisible();
await expect(page.getByText("成品实际产出重量", { exact: true })).toBeVisible();
```

- [ ] **Step 2: Run browser test and verify RED**

Run: `pnpm --dir frontend exec playwright test e2e/pc-experiment-form.spec.ts --project=pc-chromium`

Expected: new labels are absent.

- [ ] **Step 3: Build `FormulaEditor.vue`**

Use an Ant Design table with category select, primary-material radio, material name, ratio display, weight input, g/kg select, utilization defaulting to 100, remark, and delete. Enforce a single primary row by setting all other rows to false when a radio is selected.

- [ ] **Step 4: Build `ProcessTabsEditor.vue`**

Use editable tabs. Each pane contains input, next-output, residual weight, residual disposition, computed loss weight/rate, and remark. When output changes, copy it to the next untouched process input.

- [ ] **Step 5: Build `FinishedYieldEditor.vue`**

Show primary input read-only, finished output editable, and calculated yield percentage read-only. Display a warning when no primary material is selected.

- [ ] **Step 6: Replace the three inline sections in `ExperimentFormView.vue`**

Keep mode selection, status sidebar, attachment upload, draft save, and submit buttons. Map component values into the shared API types and prevent submit when no primary material exists.

- [ ] **Step 7: Run E2E, typecheck, and build**

Run: `pnpm --dir frontend test:e2e && pnpm --dir frontend typecheck && pnpm --dir frontend build:pc`

Expected: PC experiment test and existing tests pass; build exits 0.

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/pc frontend/e2e/pc-experiment-form.spec.ts
git commit -m "feat: redesign pc experiment form"
```

### Task 6: Align the mobile experiment form

**Files:**
- Create: `frontend/apps/mobile/src/components/FormulaEditor.vue`
- Modify: `frontend/apps/mobile/src/components/ProcessStepEditor.vue`
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Test: `frontend/e2e/mobile-experiment-form.spec.ts`

- [ ] **Step 1: Write a failing mobile browser test**

Verify the mobile view exposes the same categories, primary material selection, process residual fields, and yield result without a horizontally scrolling desktop table.

- [ ] **Step 2: Run test and verify RED**

Run: `pnpm --dir frontend exec playwright test e2e/mobile-experiment-form.spec.ts --project=mobile-chromium`

Expected: the new controls are absent.

- [ ] **Step 3: Implement stacked mobile formula rows**

Each material is a compact section with category chips, primary-material switch, name, weight/unit, 100% utilization default, computed ratio, and optional remark.

- [ ] **Step 4: Extend mobile process cards**

Add residual weight/disposition and show computed loss beneath the three weight fields. Preserve the quick and arrange-first modes.

- [ ] **Step 5: Add finished yield summary**

Place the primary input, finished output, and yield percentage directly before the fixed save/submit action bar.

- [ ] **Step 6: Verify mobile**

Run: `pnpm --dir frontend test:e2e && pnpm --dir frontend typecheck && pnpm --dir frontend build:mobile`

Expected: all mobile tests pass and production build exits 0.

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/mobile frontend/e2e/mobile-experiment-form.spec.ts
git commit -m "feat: align mobile formula and yield form"
```

### Task 7: Update Excel exports and regression coverage

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/ReportExportService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/ReportExportControllerTest.java`
- Modify: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`

- [ ] **Step 1: Add failing workbook assertions**

Assert experiment export headers and values include category, primary marker, formula ratio, residual weight/disposition, loss weight, finished output, and finished yield. Assert pricing export still reads actual material weight and defaults missing utilization to 100%.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -q -Dtest=ReportExportControllerTest,PricingFileServiceTest test`

Expected: new cells are missing.

- [ ] **Step 3: Update workbook generation**

Add columns without deleting the existing material fields required by the pricing template. Format ratios as percentages and weights to four decimals.

- [ ] **Step 4: Run full verification**

Run:

```bash
cd backend && mvn -q test
cd ../frontend && pnpm typecheck && pnpm build && pnpm test:e2e
```

Expected: backend tests, frontend tests, typecheck, and both builds pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/service backend/src/test/java/com/lhr/rnd
git commit -m "feat: export formula loss and yield data"
```

### Task 8: Update project execution records

**Files:**
- Modify: `docs/研发样品管理系统_执行记录.md`

- [ ] **Step 1: Record the delivered schema, UI, calculations, tests, and remaining ERP integration work**

- [ ] **Step 2: Run placeholder and diff checks**

Run: `rg -n 'TBD|TODO' docs/研发样品管理系统_执行记录.md && git diff --check`

Expected: no new placeholders and no whitespace errors.

- [ ] **Step 3: Commit**

```bash
git add docs/研发样品管理系统_执行记录.md
git commit -m "docs: record experiment form redesign"
```
