# 灵活得率与可靠打样草稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除快速打样分支，支持无主料和多主料得率，并让 PC/手机实验单在页面切换后可靠恢复草稿。

**Architecture:** 以 `YieldCalculationMode` 作为统一业务规则，后端负责最终校验和得率重算，前端共享计算与本地草稿工具。Excel 生成读取已锁定实验单的计算模式，写入对应公式。

**Tech Stack:** Java 17、Spring Boot 3、JPA/Flyway、Vue 3、TypeScript、Vant、Ant Design Vue、Apache POI、Vitest/Node test、JUnit 5。

## Global Constraints

- 主料数量允许 0、1 或多项。
- 包材永不进入得率计算。
- 利用率默认 100%。
- 草稿允许不完整，提交前严格校验。
- 历史实验单默认 `SELECTED_PRIMARY_MATERIALS`。

---

### Task 1: 共享计算模型

**Files:**
- Modify: `frontend/packages/shared/src/experiment/calculations.ts`
- Modify: `frontend/packages/shared/src/api/types.ts`
- Test: `frontend/tests/experiment-calculations.test.mts`

- [ ] 先增加无主料总领料、多主料领料重量和包材排除的失败测试。
- [ ] 增加 `YieldCalculationMode`、领料重量及得率基准函数。
- [ ] 运行共享计算测试并确认通过。

### Task 2: 后端领域与持久化

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/model/YieldCalculationMode.java`
- Create: `backend/src/main/resources/db/migration/V15__add_experiment_yield_calculation_mode.sql`
- Modify: `backend/src/main/java/com/lhr/rnd/model/ExperimentForm.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentFormEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SaveExperimentDraftRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Test: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`

- [ ] 增加多主料、无主料总领料和草稿宽松校验的失败测试。
- [ ] 持久化得率模式并统一计算得率基准。
- [ ] 放宽草稿校验，提交时按模式严格校验。
- [ ] 运行相关后端测试并确认通过。

### Task 3: 可靠草稿缓存

**Files:**
- Create: `frontend/packages/shared/src/experiment/draft-cache.ts`
- Modify: `frontend/packages/shared/src/index.ts`
- Test: `frontend/tests/experiment-draft-cache.test.mts`

- [ ] 增加用户/任务隔离、时间比较、损坏数据忽略和清除测试。
- [ ] 实现无 DOM 依赖的序列化缓存工具。
- [ ] 运行草稿缓存测试并确认通过。

### Task 4: PC 与手机实验单

**Files:**
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`
- Modify: `frontend/packages/shared/src/api/task.ts`
- Test: `frontend/tests/experiment-form-source.test.mts`

- [ ] 增加“无快速打样入口、支持多主料、显示自动保存状态”的源码行为测试。
- [ ] 删除模式选择页，默认从工序编排进入。
- [ ] 增加得率基准选择和多主料勾选。
- [ ] 增加本地即时保存、1.5 秒后台自动保存、恢复与锁版清理。
- [ ] 运行 PC/手机 typecheck、build 和前端测试。

### Task 5: 核价 Excel 公式

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`
- Test: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`

- [ ] 增加酱汁总领料公式和多主料求和公式的失败测试。
- [ ] 根据实验单模式生成得率分母公式并统一表头名称。
- [ ] 运行核价文件测试并打开渲染结果核对格式。

### Task 6: 全流程回归

**Files:**
- Modify: `docs/superpowers/reports/2026-07-13-flexible-yield-and-draft-verification.md`

- [ ] 跑后端全量测试。
- [ ] 跑前端全量测试、typecheck 和两个构建。
- [ ] 走通普通单主料、多主料、无主料酱汁三条流程。
- [ ] 记录实际命令、结果和剩余风险。
