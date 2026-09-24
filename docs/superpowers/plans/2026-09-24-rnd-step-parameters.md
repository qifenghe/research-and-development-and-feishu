# 研发可扩展步骤参数 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让研发自定义步骤参数与投料时机，并贯通试验、正式版本、配方和 SOP。

**Architecture:** 在现有步骤结构追加参数列表、在投料追加时机字段；旧两个参数通过唯一适配层兼容，不改历史快照。保留现有权限、版本冲突检查和得率计算，先数据后页面再导出。

**Tech Stack:** Java17/Spring/JDBC/Flyway、Vue3/TypeScript、POI、现有 Node 与 Maven 测试。

**Spec:** `docs/superpowers/specs/2026-09-24-rnd-step-parameters-design.md`（用户已确认含滚揉、自定义参数的补充设计）。

## Global Constraints

- 模板只提供字段，不提供生产标准值；新增参数不参与重量计算。
- 缺失值不是零；不从文字描述推算重量。
- 正式快照及归档文件不改写；兼容旧两参数，不维护两个互相冲突的数据源。
- 真空度必须区分表压、绝对压力和设备百分比；口径不明只保留记录，不自动换算或比较。
- 结构复制清除实测，继承复制保留来源；确认和权限规则不放松。
- 只用软件测试记录；不修改用户 TASK-0019，不通知飞书，不自动推送或合并 Git。
- 当前 checkout 有无关改动，逐文件提交；已完成的拖拽修复 `3e55c6f` 不重做。

## Review Focus

1. 旧 JSON 没有列表与新 JSON 明确空列表不同，删除最后一个参数不能让旧参数复活（Task1）。
2. 复制、提交重映射后计划仍指向正确参数，重名参数不得错误匹配（Task2）。
3. 表压与绝对压力即使单位相同也不可直接算偏差（Task2）。
4. 自定义第3个及以后参数保存刷新、导出不能丢失（Task1/3/4）。
5. 未保存页面配方和已保存下载不同，来源要标清；缺数据不能假合计（Task5）。

## Task 1: 参数与投料数据无损保存

**Files:** 修改 `backend/src/main/java/com/lhr/rnd/model/ProcessPlan.java`、`service/ProcessPlanService.java`；新增 `domain/StepParameterService.java`、下一可用版本的 `db/migration/V28__step_parameters_and_addition_details.sql`（执行前确认版本未被占用）。修改 `frontend/packages/shared/src/process-plan.ts`；新增 `frontend/packages/shared/src/step-parameters.ts` 并从 shared index 导出。测试新增 `backend/src/test/java/com/lhr/rnd/service/StepParameterPersistenceTest.java`、`frontend/tests/step-parameters.test.mts`。

**Interfaces:** `MinorStep.parameters` / `MinorProcessStepDraft.parameters` nullable列表；`StepMaterial.additionTiming/additionMethod` nullable文字。列表存在时为唯一权威，null/缺省才兼容旧字段。统一类型：

```ts
export interface StepParameterDraft {
  key: string;
  name: string;
  valueType: "NUMBER" | "TEXT";
  unit?: string;
  actualValue?: string;
  lowerLimit?: string;
  upperLimit?: string;
  measurementBasis?: string;
  remark?: string;
  showInSummary: boolean;
}
// Java 对应 record StepParameter，数值保留字符串，校验时使用 BigDecimal。
// 单位与口径为空不猜；数字可负（例如表压），禁止 NaN/Infinity/混杂文字。
```

- [ ] 写迁移/往返保存测试，使用现有 ProcessPlanServiceTest 的本地测试fixture：四参数转速/压力/运行/暂停及投料时机保存再读取，逐值相等；明确空列表不复活旧字段，缺省列表保留旧参数。
```ts
assert.equal(effectiveStepParameters({ ...legacyStep, parameters: [] }).length, 0);
assert.equal(effectiveStepParameters(legacyStep).length, 2);
assert.equal(effectiveStepParameters(stepWithFourParameters)[3].actualValue, "5");
```
- [ ] 执行上述两个测试，记录预期失败；`legacyStep` 在测试内定义为两个具名具值参数，四参数fixture手写，不从被测转换函数生成期待值。
- [ ] 新增 nullable `experiment_minor_step.parameters_json`（CLOB，沿用已有JSON存储方式）及 `experiment_step_material.addition_timing/addition_method`（VARCHAR(2000)）；同时更新 saveStep、loadSteps、模型转换及复制构造入口，禁止只改JSON DTO而漏掉正式表存储。
- [ ] 实现 `effectiveStepParameters(step)`（TS）和 `StepParameterService.effective(MinorStep)`（Java），旧参数键采用 `legacy-1/legacy-2`，数字类型只按完整数值解析。新列表存在时忽略旧字段；保存新列表时清空旧字段镜像。参数键在步骤内唯一，重复键拒绝；范围完整时拒绝下限大于上限。非法类型拒绝；空值允许草稿。
- [ ] 覆盖 Java Jackson 旧快照缺字段反序列化、旧构造器兼容、正式表读写、已有主料90%×80%=72%不变。运行 focused Maven/Node，契约检查与 typecheck；提交本任务文件。

## Task 2: 计划、复制、正式晋升和比较

**Files:** 修改 `model/TrialScheme.java`、`domain/TrialSchemeCopyService.java`、`service/TrialSchemeService.java`、`service/TrialPromotionService.java`；修改 `frontend/apps/pc/src/services/trialApi.ts`、`components/trials/trialDraft.ts`、`trialComparison.ts`、`TrialWorkbench.vue`、`components/process/processPlanDiff.ts`。测试使用 `TrialSchemeCopyServiceTest`、`TrialSchemeServiceTest`、`TrialPromotionServiceTest`、`trial-comparison.test.mts`、`trial-workbench-mounted.test.mts`。

**Interfaces:** `PlannedData.parameterValues: Map<stepId, Map<parameterKey,string>>`，旧 `stepParameters` 仅为读取兼容。旧计划转换至 `legacy-1/legacy-2`；新map存在时优先，避免删除后再生。提交使用现有预览token/版本/idempotency机制，不新增旁路。

- [ ] 写失败测试：四参数结构复制清空actual，显式计划优先、没有计划才复制原actual作计划；含actual复制保留继承标记；步骤/参数键重映射后计划对应，删除参数剪掉计划键。
```ts
assert.equal(copy.plan.majorProcesses[0].steps[0].parameters[0].actualValue, undefined);
assert.equal(copy.plannedData.parameterValues[newStepId][newParameterKey], "8");
assert.equal(compareParameter(beforeGauge, afterAbsolute).comparable, false);
```
`compareParameter(before,after)`新增于 `trialComparison.ts`，返回 `{comparable:boolean, delta:number|null, reason:string|null}`；二者包含参数定义、actualValue。同名同单位同口径且NUMBER完整才差值；TEXT展示变化，缺失显示待补充。
- [ ] 跑 focused RED，再实现服务端计划引用校验、复制映射、前端草稿映射/剪枝。匹配同源稳定身份；无唯一同源关系时只允许唯一相同定义匹配，重名/歧义不猜。
- [ ] 将参数定义和值、投料时机纳入正式草稿差异/提交说明，确保晋升后不可变snapshot包含新字段；试验之后修改不能改正式版本来源。
- [ ] 覆盖压力同单位不同口径不可比、设备百分比缺定义不可比、负表压保存、空与零、旧两参数比较仍兼容；执行上述focused测试、全后端/前端测试、typecheck；提交。

## Task 3: 简洁参数编辑器与投料时机

**Files:** 新增 `frontend/apps/pc/src/components/process/StepParameterEditor.vue`、`stepParameterTemplates.ts`；修改 `MinorStepWorkspace.vue`、`TrialWorkbench.vue`；新增 `frontend/tests/step-parameter-editor.test.mts`。复用既有 mounted测试基础设施。

**Interfaces:** Editor `modelValue:StepParameterDraft[]`、`readonly:boolean`；事件 `update:modelValue`。计划值由试验宿主的 `parameterValues` 保存，不塞进正式actual列表。模板函数 `parameterTemplates(operation:string):StepParameterDraft[]` 只产生字段定义及新键，不填写actual或范围。

- [ ] 写失败测试：新增第三、四参数，编辑rpm与压力口径、删除确认取消不删、排序后键不变、只读无写入。
```ts
assert.deepEqual(addedParameters.map(p => p.name), ["转速", "压力", "运行时间", "暂停时间"]);
assert.ok(addedParameters.every(p => p.actualValue == null));
assert.equal(summaryParameters(addedParameters).length <= 3, true);
```
`summaryParameters(parameters)`放入 `stepParameterTemplates.ts`，按用户勾选顺序取前三个，不把“第一个有值”当关键标准。
- [ ] 跑RED，实现列表式新增/编辑/删除/排序；常用字段从spec中的操作模板选择追加，不能整组替换已录数据。折叠显示最多三项及其余数量。
- [ ] 投料行折叠保留物料/重量，详情填写additionTiming/additionMethod；不从描述提取重量。关键控制点编辑器保持独立。
- [ ] 用户自定义数字/文字都走同一保存链路；单位支持常用选项及自填；压力口径待确认提示，禁止默认假定表压。操作温度名称区分中心/介质，转速区分滚筒/刀轴。
- [ ] 验证当前方案保存刷新、只读历史、结构复制和模板添加；运行focused/fullfrontend/typecheck/build，在专用测试记录做 CUA，不编辑TASK0019；提交。

## Task 4: SOP与配方同源输出

**Files:** 修改 `service/ProcessExportCheckService.java`、`ProcessArtifactService.java`、`SopDocumentRenderer.java`、`model/ProcessExportView.java`；测试 `ProcessExportCheckServiceTest`、`ProcessArtifactServiceTest`、`SopDocumentRendererTest`。

**Interfaces:** 共用 Task1 的 effective参数，不另行推测旧字段。预览 renderer 接收带完整SOP检查issues的view；正式生成仍 requireReady，预览不阻断，旧归档下载不重新检查。

- [ ] 写失败的实际DOCX内容测试：第4个参数、表压口径、加入时机均出现；参数有值缺名和数值缺单位在待核实区明确出现且保留原值。
```java
assertThat(text).contains("暂停时间", "表压", "水沸后加入", "待核实");
assertThat(text).contains("97"); // 缺名称不能把已填值整项隐藏
```
- [ ] 跑RED，实现全部参数和时机渲染，number/valueType及unit/basis完整性检查；范围可选，NUMBER有值必须有适用单位或明确无量纲；不虚构单位。TEXT不做数值范围比较。
- [ ] 公式实际量/100kg折算仍独立，增加可追溯步骤明细与加入时机；不重复累计中间产物，不在核价文件增加金额。
- [ ] 生成多步骤、长名称、8参数、缺单位/缺名称、继承实测的DOCX/XLSX；使用documents/spreadsheets技能逐页渲染检查；跑focused/fullbackend后提交。确认未改变旧文件读权限和版本来源。

## Task 5: 配方入口、版本说明与整体验收

**Files:** 修改 `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`、`components/trials/TrialWorkbench.vue`、`components/process/ProcessPlanWorkspace.vue`；新增 `components/process/RecipeSummaryDrawer.vue`、`frontend/tests/recipe-summary.test.mts`；扩展 `scripts/verify-rnd-trials.mjs`；新增 `docs/研发步骤参数验收-2026-09-24.md`。

**Interfaces:** Drawer接收当前 `plan:ProcessPlanDraft`、`sourceLabel:string`、`dirty:boolean`，复用shared `aggregateProcessRecipe`并附加明细，不再创造计算口径。同名不同物料身份分开；缺失总量用现有完整性适配，不用0补齐。

新增同目录 `recipeSummary.ts` 提供 `buildRecipeSummary(plan)`，返回 `{externalTotalKg:number|null, rows:Array<{materialName:string, weightKg:number|null, proportionPercent:number|null, occurrences:Array<{majorName:string, stepName:string, weightKg:number|null, additionTiming?:string, additionMethod?:string}>}>}`。下方测试中的summary/incompleteSummary分别取该函数对完整/缺失fixture的返回值。所有helper通过真实输入输出测试，不只检查源代码字符串。

- [ ] 写失败测试：两次外部同身份投料2kg+3kg=5kg，同名不同编码不合并，中间转入5kg不增加外部总量；任何未填外部重量时总量与比例待补充。
```ts
assert.equal(summary.externalTotalKg, 5);
assert.equal(summary.rows[0].occurrences.length, 2);
assert.equal(incompleteSummary.externalTotalKg, null);
```
- [ ] 跑RED，顶部增加“配方汇总”按钮；抽屉显示来源和未保存标记。下载仍由已保存预览入口执行，dirty时先保存，不偷换当前展示的数据源。
- [ ] 标签改为试验方案/正式版本并解释用途；优先已存在试验方案，没有时提示新建，GET不创建记录；保留原正式草稿及显式提交路径，不自动迁移。
- [ ] API测试覆盖扩展字段保存、版本冲突、角色隔离、复制、晋升、导出内容；全后端、前端、契约、路由、typecheck/build通过后刷新专用preview。CUA确认新增字段、展开简洁性、配方来源、文件内容；报告中区分API下载与浏览器收件证据。
- [ ] 独立代码复核本计划范围；修复确认的问题，报告最终状态并逐文件提交。真实产品人工对账标为等待用户数据，不用软件fixture冒称已通过真实工厂验证。

## 执行顺序与自查

依次 Task1→2→3→4→5。模型、计划引用、编辑器、导出共享同一字段契约，不能并行写同一文件。任何新字段都必须完成正式表/试验JSON/不可变快照三个存储面检查。

自查：spec各节对应Task1–5；拖拽已有提交；真空口径和删除重映射已有专门测试；未保存配方来源与文件来源分离；食品标准值与真实产品对账不冒充完成。执行方式待用户确认，推荐由当前助手顺序实施，最后一次独立复核。
