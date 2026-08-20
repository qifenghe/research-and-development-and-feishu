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
