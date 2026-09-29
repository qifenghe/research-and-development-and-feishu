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
- 分类扩展采用已批准设计末尾的两级结构；分类写操作仅SYSTEM_ADMIN，内勤继续维护物料，研发继续只读选用。
- 执行状态：Task 1已完成，不重做；Task 2进行中。新增Task 6先于Task 3执行，之后完成3→4→5，保留原任务编号与进度记录。沿用用户选择的本会话执行方式及一次最终独立审查。

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

### Task 6: 两级分类与超级管理员专属维护（在Task 3前执行）

**Files:** 新建 `backend/src/main/resources/db/migration/V32__material_classifications.sql`、`backend/src/main/java/com/lhr/rnd/model/MaterialClassification.java`、`backend/src/main/java/com/lhr/rnd/service/MaterialClassificationService.java`、`backend/src/main/java/com/lhr/rnd/api/MaterialClassificationController.java`、`backend/src/test/java/com/lhr/rnd/api/MaterialClassificationControllerTest.java`；修改现有MaterialCatalogItem、MaterialCatalogService、MaterialCatalogController、RolePermissionService及其测试。V31为进行中的引用迁移，执行前确认V32未占用。

**Interfaces:** `MaterialClassification(id,parentId,name,businessType,sortOrder,status,version)`；GET `/api/v1/material-classifications` 返回扁平列表，包含停用状态供分类树显示；POST同路径新建，PUT `/{id}`编辑名称/排序，POST `/{id}/status`启停。写请求带expectedVersion（新建除外），创建接收parentId、name、businessType、sortOrder，二级businessType由父级确定。业务类型固定RAW/AUXILIARY/PROCESS_WATER/PACKAGING。物料返回新增classificationId；物料写请求增加classificationId，类型由服务端分类决定。GET `/api/v1/materials` 增加可选classificationId筛选，一级包含其二级，空值查询全部。

- [ ] **失败测试：** `assistantCannotManageClassificationEvenWithCapability`：内勤即使配置写能力，分类POST/PUT/status仍403；管理员成功，内勤物料POST仍成功。`classificationDepthAndSiblingNamesValidated`：原料→牛成功、同父级“ 牛 ”重复拒绝、辅料下同名可用、三级和变更parentId拒绝。`classificationTypeCannotBypassPackagingRule`：包装分类携带RAW不能变成原料，投料仍拒绝。`disabledParentBlocksNewAssignmentOnly`：父级停用后新建/重新归类拒绝，旧启用物料仍可全局选用及保存未改分类的编辑。`rootFilterIncludesChildren`：原料筛选含牛，不含辅料。`staleClassificationWriteRejected`：旧版本不能覆盖改名。迁移测试验证旧物料归默认一级，编码与业务类型不变。
- [ ] 运行 `mvn -f backend/pom.xml -Dtest=MaterialClassificationControllerTest,MaterialCatalogControllerTest test`。Expected：新增接口/字段未实现导致失败，保留失败证据。
- [ ] 实现分类表（id、parent_id、name、规范化同级唯一键、business_type、sort_order、status、version及审计时间）。初始化原料/辅料/包装材料/工艺用水及设计中的默认二级；按已有category回填物料classification_id，不读写实验单与归档。数据库同级唯一约束覆盖两个管理员并发创建重复名称，根节点使用非空唯一范围键避免NULL父级绕过约束。
- [ ] 实现分类服务校验两级限制、固定父级和业务类型、父级启用状态、乐观锁和审计。服务端角色校验仅接受SYSTEM_ADMIN；分类写能力不能授予非管理员，分类读取沿用物料读取角色，新库默认与已有库迁移均覆盖。无需物理删除端点。
- [ ] 扩展物料接口：创建或修改classificationId时校验目标和父级启用；未改变分类的旧条目允许保留停用分类。更新已有物料省略classificationId时保留原分类；兼容旧创建请求仅带category时映射到对应启用默认一级，不接受无效category。classificationId与category同时提供且冲突时拒绝。分类停用不级联物料停用。
- [ ] 运行上述测试及 `mvn -f backend/pom.xml test`。Expected：全部通过，包括原有物料权限、单位及引用快照测试。分类改名不修改业务快照，归档仍能读取。
- [ ] 只提交本任务文件，提交说明 `feat: add admin-managed material classifications`。

### Task 3补充：分类树与分类管理入口

**Files:** 在Task 3文件之外，新建 `frontend/apps/pc/src/components/materials/MaterialClassificationManager.vue`、`frontend/tests/material-classifications.test.mts`；分类API沿用 `materialCatalogApi.ts`，共享类型放 `material-catalog.ts`。移动端选择器使用同一分类接口，可用级联选择代替常驻树。

**Interfaces:** 分类管理组件仅SYSTEM_ADMIN可见；MaterialPicker增加可选classificationId过滤，选物料发出的对象包含classificationId。左侧“全部”＋一级/二级树，右侧列表分页；选择一级含二级，清空分类恢复全部。管理页允许管理员显示停用节点，停用节点不能作为新增归类目标。

- [ ] 在既定RED阶段添加真实组件或浏览器断言：管理员建“测试原料→牛类”后内勤能在其下建料；内勤无分类管理按钮；研发能筛选选料但无维护按钮；切换分类重置页码，迟到的旧分类查询不能覆盖新结果；全局搜索可找到归类已停用但自身启用的物料。
- [ ] 实现管理弹窗名称、父级（新建时选择）、业务类型（仅新建一级）、排序和启停；物料表单选择分类并显示派生业务类型，不给内勤创建分类入口。不额外创建数据库或重复物料表。
- [ ] 运行 `node --experimental-strip-types --test tests/material-picker.test.mts tests/material-classifications.test.mts` 及 `pnpm exec playwright test e2e/pc-material-catalog.spec.ts --project=pc-chromium`（指定5193隔离地址）。Expected：权限、筛选、乱序响应与分类变更用例通过。

### Task 5补充：分类验收与审查关注项

验收脚本增加管理员创建分类、内勤归类建料、研发筛选选料的端到端链路，所有记录独立命名，不修改TASK-0010。最终审查额外检查：并发同级重名只能一个成功；父级停用与新增归类并发时不得绕过状态校验；自定义分类不能改变包装禁投料及kg约束；空分类/停用分类不导致旧物料丢失；权限编辑器不能向其他角色授予有效分类维护权。对应测试归Task 6及Task 3补充，失败后按TDD修复。

## 分类扩展自审

分类结构、默认数据、管理员限制、物料回填和服务端校验归Task 6；管理入口和筛选归Task 3；全链路及现有数据保护归Task 5。分类业务类型与既有category明确映射，分类引用不替代catalogMaterialId，两类引用职责不同。保留Task 1完成状态和Task 2在途修改，不要求重做已验证部分。
