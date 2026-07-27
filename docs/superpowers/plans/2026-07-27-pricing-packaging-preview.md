# 核价包装预览与人工确认 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在核价文件提交审核前提供可编辑预览，让系统带入实验配方和出成、按包装模板生成建议包装明细，并将人工确认后的明细锁版导出到 Excel。

**Architecture:** 包装明细以 `pricing_file` 版本为边界持久化，不写回实验单；核价草稿创建时将锁定实验单、产品规格和包装模板解析为快照。PC 端核价详情页在 `DRAFT` 状态下展示配方只读区与包装可编辑区，确认后后端重新生成 Excel 并流转到既有审核/财务流程。

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Flyway, Apache POI, Vue 3, TypeScript, Ant Design Vue, Vitest/vue-tsc, Maven/JUnit/MockMvc.

## Global Constraints

- 实验单仅提供锁版配方、利用率、成品出成、成品数量，不采集包装物料。
- 成品出成优先取版本人工确认值；未填写时取锁定实验单的成品实际出成。
- 标签为无编码系统生成项：`{产品名称}内袋标签` 与 `{产品名称}外箱标签`。
- 内袋标签数量等于参考包数；外箱标签数量等于箱数，箱数为 `ceil(参考包数 / 每箱袋数)`。
- 包装模板物料保留物料编码；系统生成标签 `material_code` 必须为空。
- 财务只能读取审核通过且已自动移交的核价文件。
- 已审核的核价文件不可修改；修改必须生成新的核价版本。
- 现有 Excel 模板版式继续使用，包装区输出预览确认后的包装明细。

---

## File Structure

- `backend/src/main/resources/db/migration/V20__create_pricing_packaging_tables.sql`：核价包装明细和包装模板规则表。
- `backend/src/main/java/com/lhr/rnd/model/PricingPackagingItem.java`：包装行领域记录。
- `backend/src/main/java/com/lhr/rnd/model/PricingPackagingSource.java`：`TEMPLATE`、`SYSTEM_LABEL`、`MANUAL` 枚举。
- `backend/src/main/java/com/lhr/rnd/model/PricingPackagingStatus.java`：`PENDING_CONFIRMATION`、`CONFIRMED` 枚举。
- `backend/src/main/java/com/lhr/rnd/model/PricingPreview.java`：核价预览详情，包含核价文件、锁版配方汇总与包装行。
- `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingPackagingItemEntity.java`：包装行 JPA 实体。
- `backend/src/main/java/com/lhr/rnd/persistence/entity/PackagingTemplateItemEntity.java`：包装模板项实体。
- `backend/src/main/java/com/lhr/rnd/persistence/repository/PricingPackagingItemRepository.java`：按核价文件读取/替换包装行。
- `backend/src/main/java/com/lhr/rnd/persistence/repository/PackagingTemplateItemRepository.java`：读取启用模板项。
- `backend/src/main/java/com/lhr/rnd/service/PricingPackagingService.java`：模板展开、标签生成、数量换算与确认校验。
- `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`：接收包装明细并填充 Excel 包装区。
- `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`：创建核价草稿、编辑预览、确认后生成/归档 Excel、审核前校验。
- `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java`：预览、保存包装草稿、确认核价接口。
- `frontend/packages/shared/src/types/index.ts`：核价预览和包装行类型。
- `frontend/packages/shared/src/api/shipment.ts`：新增核价预览/保存/确认 API。
- `frontend/apps/pc/src/views/shipment/PricingDetailView.vue`：替换为预览编辑与审核操作页。
- `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`：接口、权限、版本锁定和 Excel 回归测试。
- `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`：包装区 Excel 行输出与标签无编码测试。

## Task 1: 建立核价包装数据模型与迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__create_pricing_packaging_tables.sql`
- Create: `backend/src/main/java/com/lhr/rnd/model/PricingPackagingItem.java`
- Create: `backend/src/main/java/com/lhr/rnd/model/PricingPackagingSource.java`
- Create: `backend/src/main/java/com/lhr/rnd/model/PricingPackagingStatus.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingPackagingItemEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/PackagingTemplateItemEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/repository/PricingPackagingItemRepository.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/repository/PackagingTemplateItemRepository.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SchemaMigrationTest.java`

**Interfaces:**
- Produces `PricingPackagingItem(String id, String pricingFileId, int sequence, PricingPackagingSource source, String materialCode, String materialName, BigDecimal quantity, String packageSpec, String conversionRule, String remark, PricingPackagingStatus confirmationStatus, String modificationReason)`.
- Produces repository methods `findByPricingFileIdOrderBySequenceAsc(String pricingFileId)` and `findByEnabledTrueOrderBySequenceAsc()`.

- [ ] **Step 1: Write the failing migration test**

```java
@Test
void migrationCreatesPricingPackagingTables() {
    assertThat(columnNames("pricing_packaging_item")).contains(
            "pricing_file_id", "source", "material_code", "quantity", "confirmation_status", "modification_reason"
    );
    assertThat(columnNames("packaging_template_item")).contains(
            "template_code", "material_name", "conversion_type", "units_per_parent", "enabled"
    );
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SchemaMigrationTest#migrationCreatesPricingPackagingTables test`

Expected: FAIL because the tables do not exist.

- [ ] **Step 3: Add migration and model records**

```sql
create table pricing_packaging_item (
  id varchar(64) primary key,
  pricing_file_id varchar(64) not null,
  sequence integer not null,
  source varchar(32) not null,
  material_code varchar(128),
  material_name varchar(255) not null,
  quantity decimal(18, 6) not null,
  package_spec varchar(255),
  conversion_rule varchar(255),
  remark varchar(1000),
  confirmation_status varchar(32) not null,
  modification_reason varchar(1000)
);
```

Add `packaging_template_item` with `template_code`, optional `material_code`, `material_name`, `conversion_type` (`PER_BAG`, `PER_BOX`, `PER_METER`), `units_per_parent`, `package_spec`, `remark`, `sequence`, and `enabled`. Seed a generic `DEFAULT_BAG` template with bag, carton and tape examples but do not seed label rows; labels are generated by code.

- [ ] **Step 4: Run the migration test and verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SchemaMigrationTest#migrationCreatesPricingPackagingTables test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__create_pricing_packaging_tables.sql backend/src/main/java/com/lhr/rnd/model/PricingPackagingItem.java backend/src/main/java/com/lhr/rnd/model/PricingPackagingSource.java backend/src/main/java/com/lhr/rnd/model/PricingPackagingStatus.java backend/src/main/java/com/lhr/rnd/persistence/entity/PricingPackagingItemEntity.java backend/src/main/java/com/lhr/rnd/persistence/entity/PackagingTemplateItemEntity.java backend/src/main/java/com/lhr/rnd/persistence/repository/PricingPackagingItemRepository.java backend/src/main/java/com/lhr/rnd/persistence/repository/PackagingTemplateItemRepository.java backend/src/test/java/com/lhr/rnd/api/SchemaMigrationTest.java
git commit -m "feat: add pricing packaging persistence"
```

## Task 2: 实现包装建议与系统标签生成

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/service/PricingPackagingService.java`
- Test: `backend/src/test/java/com/lhr/rnd/service/PricingPackagingServiceTest.java`

**Interfaces:**
- Consumes `List<PackagingTemplateItemEntity>`, `String productName`, `BigDecimal referenceOutputKg`, `BigDecimal unitWeightKg`, `int bagsPerBox`.
- Produces `List<PricingPackagingItem> createSuggestedItems(...)`.
- Produces `void validateForSubmission(List<PricingPackagingItem> items)`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void createsUncodedProductLabelsAndTemplatePackagingQuantities() {
    var items = service.createSuggestedItems(templateItems(), "1kg吮指五香味酱汁-MY",
            new BigDecimal("270"), new BigDecimal("1"), 10);

    assertThat(items).anySatisfy(item -> {
        assertThat(item.source()).isEqualTo(PricingPackagingSource.SYSTEM_LABEL);
        assertThat(item.materialCode()).isNull();
        assertThat(item.materialName()).isEqualTo("1kg吮指五香味酱汁-MY内袋标签");
        assertThat(item.quantity()).isEqualByComparingTo("270");
    });
    assertThat(items).anySatisfy(item -> assertThat(item.materialName())
            .isEqualTo("1kg吮指五香味酱汁-MY外箱标签"));
    assertThat(items).anySatisfy(item -> assertThat(item.materialName()).contains("空白箱")
            .extracting(PricingPackagingItem::quantity).isEqualTo(new BigDecimal("27")));
}

@Test
void rejectsSubmissionWhenAnyPackagingRowIsUnconfirmed() {
    assertThatThrownBy(() -> service.validateForSubmission(List.of(pendingItem())))
            .hasMessageContaining("包装物料尚未确认");
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=PricingPackagingServiceTest test`

Expected: FAIL because `PricingPackagingService` does not exist.

- [ ] **Step 3: Implement deterministic suggestions and validation**

```java
var packageCount = referenceOutputKg.divide(unitWeightKg, 0, RoundingMode.DOWN);
var boxCount = packageCount.divide(BigDecimal.valueOf(bagsPerBox), 0, RoundingMode.CEILING);
items.add(systemLabel("%s内袋标签".formatted(productName), packageCount, "1 张/袋"));
items.add(systemLabel("%s外箱标签".formatted(productName), boxCount, "1 张/箱"));
```

For template entries use `PER_BAG`, `PER_BOX`, or `PER_METER`; retain template material code. If `referenceOutputKg`, `unitWeightKg`, or `bagsPerBox` is absent/invalid for a required calculation, create a `PENDING_CONFIRMATION` row with null quantity and a rule description rather than fabricating a quantity. `validateForSubmission` must reject empty names, negative quantities, unconfirmed rows, and changed template/system rows lacking `modificationReason`.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=PricingPackagingServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/service/PricingPackagingService.java backend/src/test/java/com/lhr/rnd/service/PricingPackagingServiceTest.java
git commit -m "feat: generate suggested pricing packaging"
```

## Task 3: 将核价生成改为草稿预览、确认后导出

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/model/PricingPreview.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/PricingFileStatus.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1676-1850`
- Modify: `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java:48-76,365-380`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingFileEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/repository/PricingFileRepository.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Test: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`

**Interfaces:**
- Produces `PricingPreview getPricingPreview(String pricingFileId, String viewerName, String viewerRole)`.
- Produces `PricingPreview savePricingPackagingDraft(String pricingFileId, List<PricingPackagingItem> items, String editorName, String editorRole)`.
- Produces `PricingFileRecord confirmPricingPreview(String pricingFileId, String editorName, String editorRole)`.
- Changes `PricingFileService.generate(..., List<PricingPackagingItem> packagingItems)`.

- [ ] **Step 1: Write failing workflow and workbook tests**

```java
@Test
void confirmationUsesEditedPackagingRowsInWorkbookAndPreservesSystemLabelsWithoutCodes() throws Exception {
    var pricingId = createPricingDraft(createLockedSampleVersion());
    savePackagingDraft(pricingId, List.of(
            editedItem("FBZ0003", "1kg空白箱", "30", "10 袋/箱", "数量按实际装箱调整", CONFIRMED),
            systemLabel("麻辣香肠内袋标签", "17", CONFIRMED),
            systemLabel("麻辣香肠外箱标签", "2", CONFIRMED)
    ));

    confirmPricingPreview(pricingId);
    var workbook = downloadWorkbook(pricingId);
    assertThat(findPackagingCell(workbook, "1kg空白箱", 6)).isEqualTo(30D);
    assertThat(findPackagingCell(workbook, "麻辣香肠内袋标签", 3)).isBlank();
}

@Test
void cannotConfirmPricingDraftWithPendingPackagingRows() throws Exception {
    var pricingId = createPricingDraft(createLockedSampleVersion());
    mockMvc.perform(post("/api/v1/pricing-files/{id}/confirm", pricingId)
            .header("Authorization", bearer("rnd_director")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("包装物料尚未确认")));
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest#confirmationUsesEditedPackagingRowsInWorkbookAndPreservesSystemLabelsWithoutCodes,PricingFileServiceTest test`

Expected: FAIL because draft/confirm APIs and packaging-aware generator do not exist.

- [ ] **Step 3: Implement draft lifecycle and Excel output**

Set newly created pricing files to `DRAFT` and persist generated packaging suggestions. Keep the existing locked experiment form as the immutable material source. `savePricingPackagingDraft` may only run for `DRAFT`; verify the editor is the product owner or `RND_DIRECTOR`; persist a replacement snapshot in sequence order. `confirmPricingPreview` validates items, regenerates and archives the Excel with `PricingFileService`, changes status to `PENDING_PRICING_REVIEW`, and writes an audit record.

In `PricingFileService`, delete the hard-coded four-row `fillPackagingQuantities` logic and replace it with row-by-row output from `List<PricingPackagingItem>`. Copy/extend template formatting for as many packaging rows as needed. Output `material_code` as blank for `SYSTEM_LABEL` rows; use the current template headings: 编号、NS编码、物料名称、物料内部代码、数量（供参考）、包装规格、备注.

- [ ] **Step 4: Run regression tests and verify they pass**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest,PricingFileServiceTest test`

Expected: PASS, including the existing reference-output regression test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/model/PricingPreview.java backend/src/main/java/com/lhr/rnd/model/PricingFileStatus.java backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java backend/src/main/java/com/lhr/rnd/service/PricingFileService.java backend/src/main/java/com/lhr/rnd/persistence/entity/PricingFileEntity.java backend/src/main/java/com/lhr/rnd/persistence/repository/PricingFileRepository.java backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java
git commit -m "feat: add pricing draft confirmation workflow"
```

## Task 4: 暴露预览、编辑与确认接口，并接入权限

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java:286-340`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- `GET /api/v1/pricing-files/{id}/preview` returns `PricingPreview`.
- `PUT /api/v1/pricing-files/{id}/packaging-draft` accepts `{ "items": [...] }` and returns `PricingPreview`.
- `POST /api/v1/pricing-files/{id}/confirm` returns `PricingFileRecord` in `PENDING_PRICING_REVIEW`.

- [ ] **Step 1: Write failing authorization tests**

```java
@Test
void productOwnerCanEditDraftButFinanceCannotReadItBeforeApproval() throws Exception {
    var pricingId = createPricingDraftForOwner("张研发");
    mockMvc.perform(put("/api/v1/pricing-files/{id}/packaging-draft", pricingId)
            .header("Authorization", bearer("rnd_engineer"))
            .contentType(APPLICATION_JSON).content(validPackagingPayload()))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/pricing-files/{id}/preview", pricingId)
            .header("Authorization", bearer("finance")))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run authorization test and verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest#productOwnerCanEditDraftButFinanceCannotReadItBeforeApproval test`

Expected: FAIL because the preview endpoints and draft visibility check do not exist.

- [ ] **Step 3: Implement controller endpoints and access rules**

Add exact GET/PUT/POST mappings. `RND_DIRECTOR` may view/edit/confirm any draft. `RND_ENGINEER` may view/edit/confirm only if `version.ownerName` matches the authenticated user’s name. Finance is denied for `DRAFT` and `PENDING_PRICING_REVIEW`; finance list/detail/download become visible only after the existing review action transitions the file to automatic finance notification. Preserve existing `403` response behavior so the PC client routes to `/403` without signing out.

- [ ] **Step 4: Run authorization and existing pricing tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java
git commit -m "feat: secure pricing preview editing"
```

## Task 5: 实现 PC 核价预览编辑界面

**Files:**
- Modify: `frontend/packages/shared/src/types/index.ts:322-380`
- Modify: `frontend/packages/shared/src/api/shipment.ts:20-60`
- Modify: `frontend/packages/shared/src/detail-fields.ts:266-310`
- Modify: `frontend/apps/pc/src/views/shipment/PricingDetailView.vue`
- Test: `frontend/apps/pc/src/views/shipment/PricingDetailView.spec.ts`

**Interfaces:**
- Consumes `PricingPreview` with `materials`, `referenceOutputKg`, `packageCount`, `packagingItems`, `canEdit`, and `canConfirm`.
- Uses `api.shipment.pricingPreview(id)`, `api.shipment.savePackagingDraft(id, payload)`, and `api.shipment.confirmPricingPreview(id)`.

- [ ] **Step 1: Write failing component tests**

```ts
it("shows locked formula as read-only and allows director to confirm all packaging rows", async () => {
  mockPricingPreview({ canEdit: true, canConfirm: true, packagingItems: [pendingLabel()] });
  render(PricingDetailView);
  expect(screen.getByText("配方与出成")).toBeInTheDocument();
  expect(screen.getByText("包装物料")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "确认并提交审核" })).toBeDisabled();

  await userEvent.click(screen.getByRole("button", { name: "确认" }));
  expect(screen.getByRole("button", { name: "确认并提交审核" })).toBeEnabled();
});
```

- [ ] **Step 2: Run component test and verify it fails**

Run: `pnpm --dir frontend exec vitest run apps/pc/src/views/shipment/PricingDetailView.spec.ts`

Expected: FAIL because the edit/confirm controls are absent.

- [ ] **Step 3: Implement the preview editor**

Replace the generic descriptions-only page for `DRAFT` with three cards:

1. `配方与出成`：只读配方表，显示锁版来源、参考出成、得率和参考包数；
2. `包装物料`：可编辑表格，支持新增、编辑、删除、排序；将来源显示为模板带入、系统生成、手动新增；系统生成标签显示无编码；模板/系统行任何修改均显示修改原因输入；
3. `确认并生成正式核价文件`：保存草稿与确认提交审核，存在待确认行时禁用确认按钮并展示数量。

非草稿状态保留已有下载、报表导出和审核卡片。错误处理必须使用现有 `message.error`，不清理登录 token。

- [ ] **Step 4: Run component tests and typecheck**

Run: `pnpm --dir frontend exec vitest run apps/pc/src/views/shipment/PricingDetailView.spec.ts && pnpm --dir frontend typecheck`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/packages/shared/src/types/index.ts frontend/packages/shared/src/api/shipment.ts frontend/packages/shared/src/detail-fields.ts frontend/apps/pc/src/views/shipment/PricingDetailView.vue frontend/apps/pc/src/views/shipment/PricingDetailView.spec.ts
git commit -m "feat: add pricing packaging preview editor"
```

## Task 6: 端到端核验与文档更新

**Files:**
- Modify: `docs/研发样品管理系统_执行记录.md`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Test: `frontend/e2e/pricing-preview.spec.ts`

**Interfaces:**
- Uses the production flow: locked experiment form → pricing draft → packaging edit/confirm → director/owner review → finance visibility → Excel download.

- [ ] **Step 1: Write failing browser-flow test**

```ts
test("pricing draft requires packaging confirmation before finance can download", async ({ page }) => {
  await login(page, "rnd_director", "123456");
  await openPricingDraft(page);
  await expect(page.getByRole("button", { name: "确认并提交审核" })).toBeDisabled();
  await confirmAllPackagingRows(page);
  await page.getByRole("button", { name: "确认并提交审核" }).click();
  await approvePricingAsDirector(page);
  await login(page, "finance", "123456");
  await expect(page.getByText("下载核价文件")).toBeVisible();
});
```

- [ ] **Step 2: Run browser-flow test and verify it fails before the integrated implementation**

Run: `pnpm --dir frontend exec playwright test e2e/pricing-preview.spec.ts --project=chromium --workers=1`

Expected: FAIL before Tasks 3–5 are complete; PASS after they are complete.

- [ ] **Step 3: Verify generated workbook contents**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest,PricingFileServiceTest test`

Expected: PASS; test assertions must confirm the reference output, no-code labels, edited carton quantity and packaging section rows.

- [ ] **Step 4: Update execution record**

Document the three sources of packaging lines, label name/quantity formulas, draft lock rule, and finance visibility rule. Do not add credentials or tunnels.

- [ ] **Step 5: Run final checks and commit**

Run: `git diff --check && pnpm --dir frontend typecheck && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q test`

Expected: all commands exit 0.

```bash
git add docs/研发样品管理系统_执行记录.md frontend/e2e/pricing-preview.spec.ts backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java
git commit -m "test: verify pricing packaging approval flow"
```

## Plan Self-Review

- Spec coverage: Tasks 1–2 implement the separate packaging model, template rules and no-code labels; Tasks 3–4 implement snapshot, workflow, Excel and permissions; Task 5 implements the PC preview editor; Task 6 verifies the cross-role flow.
- Placeholder scan: no `TBD`, `TODO`, “implement later”, or unspecified tests remain.
- Type consistency: `PricingPackagingItem`, `PricingPreview`, and the three preview API methods are introduced before frontend consumption; all package count and box count rules use the same names and calculation order.
