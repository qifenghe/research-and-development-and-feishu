# Material Library and kg Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一外部物料选用并支持kg成品，保留历史工艺和配方。

**Architecture:** 独立物料主表管理身份与状态，业务条目持有引用及不可自动更新的快照。前后端共享成品单位语义，服务端负责权限、关联有效性和kg一致性校验。

**Tech Stack:** Java17、Spring Boot、Flyway、JDBC、H2测试、Vue3、TypeScript、Ant Design Vue、Node test、Playwright。

**Spec:** `docs/superpowers/specs/2026-09-29-material-library-design.md`

## Global Constraints

- 在现有pricing-material-list隔离工作区执行，保护5183/8082及用户TASK-0010数据；新建专用测试记录。
- 超级管理员、研发内勤维护；研发人员搜索选用。系统默认管理员角色为SYSTEM_ADMIN，沿用能力授权体系，不另加硬编码权限通道。
- 原辅料/工艺用水单位固定kg，包装类别不进入投料选择；不做库存、价格、批次、导入和飞书同步。
- 不迁移猜测历史关联，不重写正式版本；不复用formulaMaterialId，不删历史内部字段。
- kg数量从重量同步，缺失保持缺失；不当作袋数。袋装历史逻辑不变。
- 保留现有顶部CSS和浏览器测试修改，不将测试产物、数据库提交；无新授权不推送合并。

## Review Focus

1. 同时新增大小写/空格不同的相同编码，只能一条成功，另一条返回明确冲突（Task 1）。
2. 搜索后、保存前物料被停用：不能新绑定；已关联草稿可保存其他字段但提交被阻止（Task 2）。
3. 物料改名且新建试验/从历史恢复草稿时，旧快照不被静默替换（Task 2）。
4. 搜索请求乱序或切换步骤时，旧结果不得覆盖当前选择（Task 3）。
5. 成品为小数kg但有旧包数缓存时，提交及核价不能把kg当袋数（Task 4）。

### Task 1: 物料主数据与能力授权

**Files:** 新建 `backend/src/main/resources/db/migration/V30__material_catalog.sql`、`backend/src/main/java/com/lhr/rnd/model/MaterialCatalogItem.java`、`backend/src/main/java/com/lhr/rnd/service/MaterialCatalogService.java`、`backend/src/main/java/com/lhr/rnd/api/MaterialCatalogController.java`；修改 `RolePermissionService.java`、`PermissionCapability.java`；新建 `backend/src/test/java/com/lhr/rnd/api/MaterialCatalogControllerTest.java`。

**Interfaces:** `/api/v1/materials` GET(query,category,status,page,size)返回 `{items,total,page,size}`，POST创建；`/{id}` PUT更新带expectedVersion；`/{id}/status` POST启停带expectedVersion。条目字段id/code/name/category/specification/unit/status/version；category为RAW/AUXILIARY/PROCESS_WATER/PACKAGING，status为ACTIVE/INACTIVE。能力MATERIAL_READ/MATERIAL_MANAGE。

- [ ] 写接口失败测试：`normalizedCodeIsUnique`断言“ abc ”与“ABC”只创建一个；`engineerCannotManage`断言研发可查询而写入403；`staleVersionRejected`断言过期更新不覆盖；`unitCategoryValidated`断言原料L被拒绝，包装米可用。
- [ ] 运行 `mvn -f backend/pom.xml -Dtest=MaterialCatalogControllerTest test`，确认功能不存在导致失败。
- [ ] 实现主表唯一约束、乐观版本、分页（默认20、最大100）、编码规范化、不可修改编码、无删除接口及审计；默认权限同时覆盖新库初始化和既有库迁移。
- [ ] 运行同一测试及现有角色权限测试，全部通过。
- [ ] 仅提交该任务文件，提交说明 `feat: add material catalog and permissions`。

### Task 2: 投料引用与历史快照

**Files:** 新建 `V31__material_catalog_references.sql`、`backend/src/main/java/com/lhr/rnd/service/MaterialReferenceService.java`、`backend/src/test/java/com/lhr/rnd/service/MaterialReferenceServiceTest.java`；修改 `model/ProcessPlan.java`、`model/ExperimentMaterial.java`、`persistence/entity/ExperimentMaterialEntity.java`、`service/ProcessPlanService.java`、`service/SampleWorkflowService.java`、`domain/TrialSchemeCopyService.java`、`domain/ProcessSubmissionValidator.java` 及试验保存/提升调用点。

**Interfaces:** 业务投料新增可空catalogMaterialId、catalogSpecification、catalogUnit。`MaterialReferenceService.resolveForSave(incoming,persisted)`验证身份并保留未重选快照；`requireActiveReferences(materials)`只用于新的正式提交、试验提升和旧实验单交测。使用Task 1持久化数据。

- [ ] 写失败测试：`forgedIdentityIsReplacedByCatalogSnapshot`；`renameDoesNotRewriteSavedSnapshot`；`inactiveExistingDraftCanSaveButCannotSubmit`；`legacyUnlinkedDraftCanSaveButNewSubmissionFails`；`trialCopyKeepsReferenceAndSnapshot`；`intermediateDoesNotRequireCatalog`。分别断言快照值、状态及历史正式下载仍可用。
- [ ] 运行 `mvn -f backend/pom.xml -Dtest=MaterialReferenceServiceTest test`，确认红灯。
- [ ] 添加兼容构造器/可空列及JSON快照字段；比较已存条目判定新绑定，禁止绕过停用检查。所有保存/提交路径统一调用服务，不用前端校验替代。旧归档只读路径不新增追溯阻断。
- [ ] 同一测试和TrialPromotionServiceTest、SampleWorkflowControllerTest通过；验证原料改名后复制/新草稿仍保留旧快照。
- [ ] 提交 `feat: bind process materials to catalog snapshots`。

### Task 3: 物料管理和统一选择入口

**Files:** 新建 `frontend/packages/shared/src/material-catalog.ts`、`frontend/apps/pc/src/services/materialCatalogApi.ts`、`frontend/apps/pc/src/views/materials/MaterialCatalogView.vue`、`frontend/apps/pc/src/components/materials/MaterialPicker.vue`；修改PC路由、AdminLayout.vue、MinorStepWorkspace.vue、ProcessPlanSnapshot.vue、processPlanDiff.ts、ExperimentFormView.vue，以及共享ProcessPlan/ExperimentMaterial类型和移动端旧实验单投料入口；新建 `frontend/tests/material-picker.test.mts`、`frontend/e2e/pc-material-catalog.spec.ts`。

**Interfaces:** MaterialPicker接收currentMaterial和readonly，发出select(MaterialCatalogItem)，无任意文本创建；服务API对应Task 1。父组件写入Task 2字段。管理页独立入口，查询与维护按钮由能力控制。

- [ ] 写失败测试：选定物料填入只读身份并保留重量；空库维护者显示新增入口、研发显示联系内勤；旧条目显示待关联且不丢值；慢请求返回不覆盖新搜索；切换中间产物清除catalog引用。
- [ ] 运行 `node --experimental-strip-types --test frontend/tests/material-picker.test.mts`确认失败。
- [ ] 实现分页搜索、请求序号防乱序、管理弹窗和冲突提示；统一正式工艺、试验、旧实验单选择器；移除配方ID输入/详情/差异展示，保持内部兼容字段。借用原组件组合，不复制整个工艺编辑器。
- [ ] 单测通过；浏览器验证管理员/内勤维护、研发选用及禁止维护；原顶部修复保持正常。
- [ ] 提交 `feat: select external ingredients from material catalog`，不夹带其他待提交修改。

### Task 4: kg成品全链路

**Files:** 新建 `frontend/packages/shared/src/finished-output.ts`、`backend/src/main/java/com/lhr/rnd/domain/FinishedOutputValidator.java`、`frontend/tests/finished-output.test.mts`、`backend/src/test/java/com/lhr/rnd/domain/FinishedOutputValidatorTest.java`；修改SaveExperimentDraftRequest.java、SampleWorkflowService.java、PC/mobile ExperimentFormView.vue、共享成品类型与核价预览计算模块、实验单/SOP导出相关成品展示。

**Interfaces:** 共享`FinishedOutputUnit`加入kg；`normalizeFinishedOutput(weightKg,quantity,unit)`生成kg只读数量；`switchFinishedOutputUnit(state,nextUnit)`切到kg用重量、切回计数单位清空数量。后端`FinishedOutputValidator.validate(weightKg,quantity,unit)`拒绝kg不一致；核价从计数单位成品设置包数，kg不得设置actualBags。

- [ ] 写失败测试：12.345kg保存重载数量12.345；null重量对应null数量；12kg与10kg数量不一致被拒绝；kg切袋数量清空；旧178袋不变；kg不能自动变12袋，单包净重缺失则参考袋数不可用。
- [ ] 分别运行对应Node/JUnit测试，确认失败。
- [ ] 扩展请求白名单、共享类型、PC与移动控件；kg只保留一次重量输入，隐藏平均每kg重量。审查所有finishedOutputUnit/Quantity消费者，确保Excel、SOP、预览、核价语义一致。
- [ ] 两端重载和成品单位切换测试通过，旧计数单位回归通过。
- [ ] 提交 `feat: support kg finished output without package conversion`。

### Task 5: 集成验收与交付

**Files:** 新建 `scripts/verify-material-catalog.mjs`、`docs/物料库与kg验收-2026-09-29.md`；补充已有相关集成测试，不修改用户样例。

**Interfaces:** 使用Task 1—4公开API；固定5193隔离实例，创建带“物料库验收”标记的账号/物料/任务，记录ID，不删除历史数据。

- [ ] 写验收断言：内勤建料→研发选料→kg草稿→改名/停用→旧草稿保留→提交拦截→选择启用物料→正式提交→配方/SOP下载→核价不混用kg和袋数。另验证用户TASK-0010原数据未改。
- [ ] 执行脚本与浏览器用例，逐个记录实际结果，失败按根因修复，不把占位值当通过。
- [ ] 运行 `mvn -f backend/pom.xml test`；在frontend运行 `node --experimental-strip-types --test tests/*.test.mts`、`pnpm typecheck`、`pnpm check:api-contracts`、`pnpm check:routes`、`pnpm build`；全部通过。浏览器相关e2e用例需指定隔离地址。
- [ ] 对照设计自查及最终独立审查，记录未解决问题；H2验收不能冒称PostgreSQL实测，输出内容验证不能冒称原生打印通过。
- [ ] 保存测试说明与本地提交，保留隔离预览；告知入口、权限和迁移限制，不自动推送或合并。

## 自审结果

设计中的权限、主数据、历史兼容、kg语义与排除项均映射至任务。五类审查风险分别在Task 1—4测试中覆盖；任务依赖为1→2→3，4可独立实现，5统一验收。执行前再次核对工作区现状和迁移编号，若有新迁移占用V30/V31则顺延，不改已有迁移。
