# 样品需求模板对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按客户提供的 Excel 模板完成两端样品需求录入、持久化和后续查看。

**Architecture:** 扩展现有 sample_request 独立字段，保留原审批与任务关联。共享前端字段定义和校验，两端使用各自 UI 组件；任务详情关联读取原需求，不复制可编辑需求副本。

**Tech Stack:** Spring Boot、Java 17、Flyway、H2、Vue 3、TypeScript、Ant Design Vue、Vant、JUnit、Node test、Playwright。

**Spec:** `docs/superpowers/specs/2026-09-30-sample-request-template-design.md`（用户已确认）

## Global Constraints

- 只在既有隔离工作区开发和 5193 / 8092 验收；不修改原 5183 环境。
- 模板前 14 项必填，末两项选填。保留系统已有产品类型字段。
- 新文本字段长度上限统一 2000 字，前后空白去除；选填空白规范为 null。
- 历史字段保持 null，显示“未填写（历史需求）”，不得补为 false、“无”或模板示例。
- 新建需求严格校验；历史需求仍可审核流转。申请人沿用后端身份规则。
- 不增加附件、需求批量导入、需求编辑版本、审批节点或需求导出；不改变核价、得率及物料数据。
- 每个任务先失败测试、后最小实现、再通过测试与本地提交；不推送、合并或提交数据库。

## Review Focus

- 标样选“否”是有效输入，而 null/未选必须拒绝（任务 1、2）。
- 空格、2001 字及可选空串须分别拒绝或规范化，示例不得被当作默认值（任务 1、2）。
- “20×30×40 mm”“18元/袋，未税”“3吨/月”原意保留，不转数值或推算金额（任务 1、3）。
- 审核重建 SampleRequest 及服务重启加载不得丢新增字段（任务 1、3）。
- 历史空值不得显示成“否”，旧需求审批不能因新增必填而失败，越权仍拒绝（任务 1、3）。

---

### Task 1: 后端字段与新建契约

**Files:**
- Create: `backend/src/main/resources/db/migration/V34__extend_sample_request_template.sql`（执行前确认编号未占用，若已占用使用下一编号）
- Modify: `backend/src/main/java/com/lhr/rnd/api/CreateSampleRequestRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/SampleRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/SampleRequestEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Update existing internal new-request callers and test fixtures identified by `rg 'CreateSampleRequestCommand|new SampleRequest' backend`.

**Interfaces:**
- POST `/api/v1/sample-requests` keeps existing fields and adds `referenceSampleProvided: Boolean`, `cutShape`, `mainMaterialRequirement`, `textureRequirement`, `packagingRequirement`, `storageRequirement`, `customerTargetPrice`, `monthlyDemand`, `specialRequirements`, `referenceMarketPrice`, `additionalNotes` as nullable strings on reads.
- Writes require nonnull Boolean and first eight new text fields, optional last two strings; text maximum 2000. Existing required fields remain required.
- Read model includes identical names. Database snake_case columns nullable; no data backfill.
- `CreateSampleRequestCommand` carries identical additions. Its service creation entry enforces the same rules, so internal callers cannot bypass validation.

- [ ] Add failing HTTP tests: `templateFieldsRoundTripThroughApproval` asserts exact submitted dimension/price/demand text after create, detail and approval; `falseReferenceSampleIsValid` asserts false survives; parameterized `newRequestRejectsMissingTemplateFields` asserts missing/blank inputs fail with field-specific errors; `optionalBlankNormalizesToNull` and `templateTextLengthLimit` assert null/2000/2001 behavior.
- [ ] Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -f backend/pom.xml -Dtest=SampleWorkflowControllerTest test`; confirm added tests fail for missing behavior, not fixture setup.
- [ ] Add nullable migration and extend DTO/model/entity/command mappings. Normalize and validate at creation only. Preserve additions when approving and hydrating from persistence. Update existing legal creation fixtures with explicit valid inputs, never relax checks to make tests pass.
- [ ] Add persistence reload and historical-null approval tests; use repository/database reload rather than only the in-memory map. Run full `mvn -f backend/pom.xml test`; expect all pass including role regressions.
- [ ] Commit backend implementation and tests only: `feat: align sample request storage with customer template`.

### Task 2: 电脑及移动录入

**Files:**
- Create: `frontend/packages/shared/src/sample-request-template.ts`
- Modify: `frontend/packages/shared/src/index.ts`, `frontend/packages/shared/src/types/index.ts`, `frontend/packages/shared/src/api/sample.ts`
- Modify: `frontend/apps/pc/src/views/demand/RequestNewView.vue`
- Modify: `frontend/apps/mobile/src/views/RequestNewView.vue`
- Test: `frontend/tests/sample-request-template.test.mts`
- Test: `frontend/tests/sample-request-entry-mounted.test.mts`

**Interfaces:**
- Shared `SampleRequestTemplateFields` matches task 1. `emptySampleRequestTemplate(): SampleRequestTemplateFields` returns empty strings and null Boolean, never example values.
- `sampleRequestTemplateGroups` exports four groups of field descriptors `{key,label,required,placeholder}` matching spec.
- `validateSampleRequestTemplate(value: SampleRequestTemplateFields): Record<string,string>` returns field-keyed errors; false valid, nullable Boolean invalid, required trimmed empty invalid, new text over 2000 invalid.
- Extend create payload type with required template properties; read fields remain nullable for old data.

- [ ] Write shared tests asserting 14 total template required fields/2 optional, null versus false, whitespace and length boundaries, example strings absent from defaults. Mount both views to assert submission payload contains all fields and failed submission retains entered data.
- [ ] Run `node --experimental-strip-types --test tests/sample-request-template.test.mts tests/sample-request-entry-mounted.test.mts` from frontend; confirm RED.
- [ ] Implement shared descriptors/defaults/validation and four-section PC/mobile form. Use explicit yes/no control, text inputs/areas for dimensional and commercial descriptions, automatic applicant display and existing product-type entry. On success clear user-entered fields but retain authenticated applicant; on failure preserve inputs and show errors.
- [ ] Run targeted tests then `pnpm typecheck` and `pnpm check:api-contracts`; update contract checker only for the new approved DTO contract, not to ignore mismatches.
- [ ] Commit: `feat: match sample request entry to supplied template`.

### Task 3: 审核及研发详情的需求展示

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/model/RndTaskDetailView.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `frontend/packages/shared/src/types/index.ts`
- Create: `frontend/apps/pc/src/components/requests/RequestRequirementSummary.vue`
- Create: `frontend/apps/mobile/src/components/RequestRequirementSummary.vue`
- Modify: `frontend/apps/pc/src/views/demand/DemandDetailView.vue`, `RequestReviewView.vue`
- Modify: `frontend/apps/pc/src/views/rnd/TaskDetailView.vue`
- Modify: `frontend/apps/mobile/src/views/RequestReviewView.vue`, `TaskDetailView.vue`
- Test: `frontend/tests/sample-request-summary-mounted.test.mts`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Add nullable `sourceRequest: SampleRequest` to task detail response, resolved through existing project request ID under existing task read authorization.
- Both summary components accept `{request: SampleRequest}` and use shared group descriptors. Display false as“否”, true as“是”, null/empty historical value as“未填写（历史需求）”; retain line breaks.

- [ ] Add tests showing approved request retains requirements in authorized task detail; denied roles cannot use this addition to read another task. Mount summaries with false and null and assert distinct display; assert full dimension/price/unit text.
- [ ] Run backend targeted tests and frontend summary tests; expect RED.
- [ ] Implement trusted source lookup and summaries. Keep review list compact with expandable details. Use same summary in requirement and R&D detail views; no duplicated editable state or extra fetch that broadens permission.
- [ ] Run backend full suite and `node --experimental-strip-types --test tests/*.test.mts`; expect PASS. Update existing constructors/test fixtures for the additive detail property without removing permission assertions.
- [ ] Commit: `feat: show complete sample requirements in review and R&D`.

### Task 4: 端到端验收及交付

**Files:**
- Create: `frontend/e2e/pc-sample-request-template.spec.ts`
- Create: `frontend/e2e/mobile-sample-request-template.spec.ts`
- Create: `docs/样品需求模板验收-2026-09-30.md`

**Interfaces:** consumes tasks 1–3 real UI and existing authenticated API; no bypass or direct database business writes.

- [ ] Write browser tests for complete submit, false selection, required-field errors, optional blanks, submitted detail reload and approval→R&D visibility. Use uniquely named new acceptance records; no overwrite of existing requests.
- [ ] Run tests against current preview to confirm expected missing behavior if it still serves the old jar; after updated build run against 5193 and configured mobile preview, require PASS.
- [ ] Run full backend tests, frontend tests, typecheck, API contract check, route check and build; retain exact command results. Perform one independent final review using approved execution method and fix Important/Critical findings with failing regression tests before rerunning verification.
- [ ] Restart only the identified 8092 preview process with its existing data directory. Verify 1522 imported material records remain present and existing task data unchanged; never reset the database to fix fixtures.
- [ ] Inspect PC/mobile layouts and record actual passed checks and limitations in the acceptance document. Commit tested code and documentation, excluding database, tokens, screenshots and generated test artifacts. Report preview links, test evidence and any deferred minor issues; no push/merge.

## Self-review

All 16 template fields are covered by tasks 1–3; all five review-focus cases have named assertions. Field names are consistent across DTO/shared types/forms. Task 4 verifies live persistence, old data preservation and both clients. No unrelated pricing, materials or workflow redesign is included.
