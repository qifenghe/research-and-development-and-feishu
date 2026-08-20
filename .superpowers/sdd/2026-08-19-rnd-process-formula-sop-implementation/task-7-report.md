# Task 7 — 核价工艺版本联动报告

## RED

- `PricingFileServiceTest#usesFormalRevisionRecipeAndFinishedYieldInsteadOfLegacyInputs`：新增正式版本重载前无法编译。
- `PricingFileServiceTest#rejectsFormalExternalMaterialWithoutStableCostIdentity`：正式配方无稳定物料标识时原实现仍生成文件。
- `PricingFileServiceTest#usesFormalRevisionRecipeAndFinishedYieldInsteadOfLegacyInputs`：正式配方未复用按物料编码匹配的利用率，得到 `1.0` 而非 `0.75`。
- `SampleWorkflowControllerTest#packagingConfirmationPinsLatestFormalRevisionAndExposesItsPricingSource`：原记录 `process_revision_id` 为 `null`。

## GREEN

- 包装确认生成文件的事务中，按当前样品版本锁定实验单选择最新正式版本；`ProcessRevisionService` 读取会验证快照 SHA-256，并按实验单过滤。
- 正式版本配方仅从 `EXTERNAL` 投料汇总；中间产物无法进入核价。
- 正式配方以 `formulaMaterialId/materialCode` 与已有实验单物料编码匹配；绝不按名称匹配。缺少稳定身份或找不到成本物料时抛出 `PROCESS_REVISION_PRICING_MATERIAL_UNPRICED`。
- 成品得率按百分比转比例一次写入工作簿，不重复相乘步骤得率；工作簿写入正式来源、版本号、版本 ID 与得率。
- `pricing_file.process_revision_id` 在同一生成事务持久化。详情返回 `pricingFile.processRevisionId`、`pricingFile.source` 与 `source`（版本号/得率）；旧记录返回 `LEGACY`。
- 无正式版本仍使用原材料、得率公式和文件内容；包装确认、审核和财务状态未改变。

## 验证

```text
mvn -q -Dtest=PricingFileServiceTest,SampleWorkflowControllerTest,ProcessRevisionServiceTest,ProcessArtifactServiceTest,SchemaMigrationTest test
```

通过（退出码 0）。覆盖正式版本工作簿覆盖、稳定物料映射拒绝、FK 持久化、LEGACY 回退、两版本选择及旧记录稳定性，以及既有包装/审批/财务回归。

## 自审

- 未新增 V24；使用既有 V23 的 nullable `pricing_file.process_revision_id`。
- 未修改路由、审批或财务状态机；旧构造器与旧 `PricingFileService.generate` 签名保留。
- 未加入 `node_modules`；`git diff --check` 通过。

## 修复轮次 1

### RED

- 并发用例暴露了原流程只依赖 Java `synchronized`，无法对多实例提供数据库线性化；并发证据也未同时校验 FK、工作簿、包装行、归档元数据和下载字节。
- 正式得率为 `null` 时，来源文本被写成 `0%`，且工作簿回退到 legacy 公式，没有保持“不可用”语义。
- 多个 legacy 行可命中同一稳定编码；`formulaMaterialId` 和 `materialCode` 也可各自命中不同行。正式配方同 canonical code 的多行没有在核价边界聚合，角色冲突会默默取第一条。

### GREEN

- 包装确认事务先通过 `pricing_file` 悲观写锁重新读取状态，然后才选择 revision；锁保持到状态、FK、包装行和归档元数据提交。缓存仅在提交后更新，事务回滚会删除未能提交的归档字节。
- 每个核价记录使用自己的归档子路径，后续 revision 的新核价不会再覆盖早期核价工作簿；早期 FK、来源文本和下载字节保持旧 revision。
- 正式配方只聚合 `EXTERNAL`；稳定 ID/code 必须唯一命中 legacy 行，ID 与 code 命中不同行、重复 legacy code、角色/单位等核价属性冲突均稳定拒绝。两 formula ID 与跨步骤 mixed ID/code 映射到同 canonical code 时合并为一行，重量求和，`formulaRatio` 保持 legacy `0..1` 语义。
- 正式 `null` 得率来源写为 `UNAVAILABLE`，数值单元格保持空白，详情保持 `null`；真实 `0/100/125%` 在来源和单元格中一致，且只做一次百分比转换。
- 被篡改快照在包装确认 API 返回 `PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR`，DB 状态/FK/包装行、缓存、归档元数据/文件均不变。两个 form 各有多个 revision 时，目标核价只 pin 自己 form 的 latest。

### 验证

- `PricingFileServiceTest` 19、`SampleWorkflowControllerTest` 88、`ProcessRevisionServiceTest` 11、`ProcessArtifactServiceTest` 19、`SchemaMigrationTest` 9，合计 146 tests，0 failures / 0 errors。
- latch 并发确认连续 3 轮：每轮仅 1 成功，第二个稳定返回 `PRICING_PACKAGING_CONFIRM_ILLEGAL`；同时插入新 revision 时，最终 FK、缓存、工作簿来源/数值、包装行、归档备注/尺寸和两个下载入口的字节全部指向同一 revision。
- `mvn -q test` 全量 261 tests，0 failures / 0 errors / 0 skipped。全量顺序曾暴露 `ReportExportControllerTest` 依赖共享的同名负责人数据；夹具现会在清理引用后重建唯一且带飞书标识的负责人，单测与全量均稳定通过。
- `git diff --check` 与暂存区空白检查通过；未修改 API 路由或前端类型，未纳入 `node_modules`。
