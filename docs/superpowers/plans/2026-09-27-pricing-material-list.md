# 核价数据确认与原材料清单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将研发确认的核价基础数据按公司原材料清单模板导出，不改变实际试验数据及历史归档。

**Architecture:** 独立的核价确认快照绑定来源修订，计算服务负责重量口径和包装建议，渲染器只使用已校验快照。现有审核与文件归档流程继续负责权限和状态转换，新页面作为生成前的确认入口。

**Tech Stack:** Java/Spring Boot、JPA/Flyway、BigDecimal、Apache POI；Vue 3/TypeScript/Ant Design Vue；JUnit、Node 测试及浏览器验证。

**Spec:** `docs/superpowers/specs/2026-09-27-pricing-material-list-design.md`（用户已确认）

## Global Constraints

- 不增加价格、金额、成本、利润；不修改原始试验重量和工艺主料得率。
- 利用率要求 0 < r <= 1，空值不默认 100%；中间产物不重复领料。
- 新文件绑定确认快照；旧归档保持原字节；沿用现有权限及审核边界。
- 完整包数向下取整并显示余量，箱数向上取整；实测数量独立保存。
- 不修改用户 TASK-0019、TASK-0022 或其他真实记录；验收使用专用测试记录。
- 执行前使用 using-git-worktrees 检查隔离方式，保留当前未提交的 ID 展示、布局及其他修改。禁止全量暂存或擅自推送。

## Review Focus

1. 来源修订在确认与生成间变化：必须拒绝静默换源（任务2、4）。
2. 多主料或混合产出归属不清：不得把混合总重当单原料出成（任务1、2）。
3. 0.99 包装备注与未知单位：不得触发隐式损耗换算（任务1、3）。
4. 双击、并发保存和归档中途失败：不能产生两份成功归档或丢草稿（任务2、4）。
5. 大量物料、重复盐、长名称、缺失编码：保持分行、数据及可读性，不能残留样例内容（任务3、5）。

### Task 1: 核价模型与纯计算

**Files:** 新建 `backend/src/main/java/com/lhr/rnd/model/PricingBasisDraft.java`、`PricingBasisResult.java`；新建 `backend/src/main/java/com/lhr/rnd/service/PricingBasisCalculator.java`；测试 `backend/src/test/java/com/lhr/rnd/service/PricingBasisCalculatorTest.java`。

**Interfaces:** `PricingBasisCalculator.calculate(PricingBasisDraft draft): PricingBasisResult`。Draft 包含 schemaVersion、sourceRevisionId、文件信息、材料行、最终主料出成及对应主料行ID、独立可分装净重、单包净重、每箱包数、实测包数、包装行。材料行包含稳定行ID、源工序/步骤/物料ID、原始重量、口径 AFTER_PROCESS/BEFORE_PROCESS、确认用量、利用率、处理后产出来源及确认依据。Result 包含逐行用量/利用率/领料量、合计、核价得率、参考包箱数/余量和按字段定位的 issues。金额字段不存在。

- [ ] 写失败测试：`afterProcessUsesUtilizationOnce` 断言用量100、r=.95、出成89的得率为84.55%；`beforeProcessDoesNotDeductAgain` 断言 Q=100、U=95时领料仍100、r=.95。
- [ ] 写边界测试：空/0/负利用率、r>1、空与真实零、无对应主料范围、混合产出无确认依据均产生明确 issue；`packingUsesIndependentNetWeight` 断言89/.5=178袋、20袋/箱=9箱，缺成品净重不能用主料出成替代；89.2kg显示0.2kg余量。备注0.99不影响数量。
- [ ] 在 backend 执行 `mvn -Dtest=PricingBasisCalculatorTest test`，确认因缺实现而失败。
- [ ] 实现模型和计算；BigDecimal 使用至少12位中间小数精度和明确舍入，不使用前端浮点结果归档；只在展示时舍入。实现逐行来源校验和汇总完整性门槛。
- [ ] 重跑测试并执行 `mvn test`，确认通过后仅提交任务文件：`feat: add pricing basis calculations`。

### Task 2: 确认快照持久化及授权接口

**Files:** 新建 `backend/src/main/resources/db/migration/V29__pricing_basis_snapshot.sql`（执行时确认版本未占用）；新建 `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingBasisEntity.java`、`persistence/repository/PricingBasisRepository.java`；新建 `service/PricingBasisService.java`、`api/PricingBasisController.java`、`api/SavePricingBasisRequest.java`；测试 `service/PricingBasisServiceTest.java`、`api/PricingBasisControllerTest.java`，修改 `persistence/SchemaMigrationTest.java`（测试路径均在 backend/src/test/java/com/lhr/rnd）。

**Interfaces:** GET/PUT `/api/v1/pricing-files/{id}/basis`，POST 同路径 `/preview`、`/confirm`。PUT 输入 expectedVersion、draft；返回 draft、version、calculation、confirmation。`PricingBasisService.load(id,actor)`、`save(id,expectedVersion,draft,actor)`、`confirm(id,expectedVersion,actor)`；actor 由认证上下文提供，不信任请求中的身份。预览接口在任务3接入渲染器前返回明确未就绪，不能生成假文件。

- [ ] 写失败测试：保存重读保留逐行来源/备注，旧记录无快照不变成已确认；确认后改值失效；过期version冲突；来源修订变化要求重新取数；无权限/他人任务不能读取或写入；旧利用率仅作待确认预填。
- [ ] 执行 `mvn -Dtest=PricingBasisServiceTest,PricingBasisControllerTest,SchemaMigrationTest test`，记录预期失败。
- [ ] 增量建表，一条核价记录对应一份版本化草稿JSON、绑定来源ID、确认人时间、乐观锁版本；服务端重新计算，不接收客户端领料合计。来源数据只读；沿用现有核价查看/确认权限规则，冻结已归档记录编辑。多主料确认必须明确成员和依据。
- [ ] 重跑上述测试和 `mvn test`，验证 H2 迁移；PostgreSQL 无可用测试实例时明确标为未验证，不以兼容模式替代。提交 `feat: persist pricing basis confirmation`。

### Task 3: 单表原材料清单渲染

**Files:** 新建 `backend/src/main/java/com/lhr/rnd/service/PricingMaterialListRenderer.java`、测试 `backend/src/test/java/com/lhr/rnd/service/PricingMaterialListRendererTest.java`；复用并检查 `backend/src/main/resources/templates/pricing-material-list-template.xlsx`，必要时新增清理后的专用模板 `pricing-confirmed-material-list-template.xlsx`；修改任务2预览入口。

**Interfaces:** `render(PricingBasisDraft draft, PricingBasisResult result, boolean preview): byte[]`；纯渲染，不读取最新工艺、不自行推断缺失数据。正式渲染有阻塞issue则拒绝，预览显示待补充及非正式标识。

- [ ] 写失败测试：单工作表、正确抬头、动态工序合并、两个工序的盐分行、水保留、中间产物不重复、利用率与领料公式及84.55%结果；包装内部代码独立；无价格金额列。
- [ ] 增加0/1/100条物料、长名称、空编码、非法工作表名称字符、模板旧产品/旧人员数据清除测试；确认未批准文件不显示伪造批准。
- [ ] 执行 `mvn -Dtest=PricingMaterialListRendererTest test`，确认失败。
- [ ] 实现动态明细与包装区、合计、参考出成、得率、包数；保持模板主要样式，设置打印范围和分页。公式使用数值单元格，不用IFERROR掩盖缺失；文本以文本写入。用POI求值核对后台计算。
- [ ] 重跑测试与 `mvn test`；渲染样例和长表检查分页/截断/合并，必要时按电子表格技能处理模板资产。提交 `feat: render confirmed pricing material list`。

### Task 4: 接入正式生成与历史归档

**Files:** 修改 `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`、`PricingFileService.java`、任务2服务；新增 `backend/src/test/java/com/lhr/rnd/service/PricingBasisArchiveTest.java`；回归现有 `PricingFileServiceTest.java`、`PricingBasisTest.java`。

**Interfaces:** 新生成路径读取该核价记录已确认的 draft/result/sourceRevisionId，而不是在包装确认时重新选择最新修订。使用现有归档预留、事务、审核和财务移交流程，不新建旁路状态机。已归档文件的下载接口不变。

- [ ] 写失败测试：未确认不能生成；包装修改使确认失效；来源变化阻止生成；重复确认/生成最多一份归档；模拟写文件或提交失败后草稿仍在且无成功状态；老归档下载字节完全一致。
- [ ] 执行 `mvn -Dtest=PricingBasisArchiveTest test` 确认失败。
- [ ] 将新核价记录接入确认渲染；旧未生成记录需要完成取数确认后走新模板，已生成记录保持原下载。改动限制在路由接入，历史模板生成测试继续保留兼容覆盖。
- [ ] 重跑针对性测试及 `mvn test`，提交 `feat: bind pricing archives to confirmed basis`。

### Task 5: 核价确认页面及交互

**Files:** 新建 `frontend/apps/pc/src/components/pricing/PricingBasisEditor.vue`、`frontend/apps/pc/src/services/pricingBasisApi.ts`、`frontend/packages/shared/src/pricing-basis.ts`；修改 `frontend/apps/pc/src/views/shipment/PricingDetailView.vue`；新增 `frontend/tests/pricing-basis.test.mts`、`pricing-basis-mounted.test.mts`。如在小步骤提供入口，仅修改 `components/process/MinorStepWorkspace.vue` 的导航/提示，不复制可编辑核价存储。

**Interfaces:** pricingBasisApi 提供 load/save/preview/confirm，对应任务2端点；共享类型逐字段对应后端。Editor 输入 pricingFileId，保存成功返回版本化服务端结果。按钮名称“保存草稿”“预览原材料清单”“确认生成”，生成复用任务4现有流程。

- [ ] 写失败测试：恢复草稿、两种重量口径切换清理失效确认、百分数转换、只读领料结果、缺项定位、同料跨工序分行、参考与实测数量区分、调整原因必填、待批准显示；使用真实组件行为而非搜索源码。
- [ ] 写并发/异步测试：保存返回前切换记录不能覆盖新记录，冲突保留本地内容；确认按钮重复点击防重且后端错误可见；未授权角色不出现编辑入口。
- [ ] 在 frontend 执行 `node --experimental-strip-types --test tests/pricing-basis*.test.mts` 确认失败。
- [ ] 实现四区页面、折叠来源、保存状态、缺项预览和生成二次确认；不在前端独立计算归档值。未知包装换算仅人工确认，不解释0.99备注。
- [ ] 执行 `node --experimental-strip-types --test tests/*.test.mts`、`pnpm typecheck`、`pnpm check:api-contracts`、`pnpm check:routes`、`pnpm build`；预览浏览器检查布局，提交 `feat: add pricing basis confirmation UI`。

### Task 6: 端到端验收与交付

**Files:** 新建 `scripts/verify-pricing-basis.mjs` 和 `docs/核价原材料清单验收-2026-09-27.md`，不改用户源模板。

- [ ] 先编写验收脚本：创建标明用途的独立测试数据，覆盖新建→取数→保存→恢复→确认→生成→授权下载；分别断言100/.95、89kg、84.55%、178袋/9箱，以及已归档不可变。脚本不调用删除用户记录的接口。
- [ ] 在隔离测试服务运行，确认能检出未满足契约的状态；全部通过后保留测试记录ID及日志，不伪造故意失败。
- [ ] 运行后端全套 `mvn test` 和任务5全部前端验证命令；浏览器实际检查研发编辑、审核和财务只读角色，下载并核对文件字节、Excel公式与版面。真实 PostgreSQL、Excel原生重算无法检查时分别注明。
- [ ] 对照设计逐项记录已通过/未验证/问题；审查diff无用户数据、无无关文件，提交验收脚本和报告。
- [ ] 按执行技能完成整体代码评审及必要修复，再构建本地预览并给用户测试入口。不自动推送 GitHub，不声称模拟数据验证了真实生产标准。

## 自查与执行方式

设计各部分已映射至任务1—6；额外风险见 Review Focus 对应测试。阶段按顺序执行，任务1模型是后续统一契约，未经说明不另建重复计算通道。

建议主代理在当前会话逐项执行，最后独立评审；也可由用户选择逐任务子代理实现与评审。用户审阅计划并选择执行方式后才开始实施。
