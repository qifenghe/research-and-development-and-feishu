# 核价 Excel 正式文件格式验收

## 产物

- XLSX：`outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx`
- 目标 PDF：`outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13-final.pdf`
- 目标截图：`outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13-final-page-1.png`
- 参考截图：`/private/tmp/pricing-reference-render/reference-page-1.png`（外部临时文件，不纳入仓库）

## 数据来源与范围

本次使用的是**格式验收测试数据**，不是生产真实配方。稳定 fixture 路径为
`backend/src/test/java/com/lhr/rnd/service/PricingWorkbookFixture.java`，工厂方法为
`fragrantBraisedLargeIntestineFixtureA0()`。其中 `YL-001`、`主原料`、`100 kg`、`82%`
利用率和 `89 kg` 参考出成均来自已提交 fixture，是用于复现旧生成文件视觉/字段的最小合成数据。
`500g/袋，20袋/箱` 是为覆盖规格与包材排版而补充的测试字段，不声称是生产真实配方。

旧生成文件 `/private/tmp/pricing-review-2.xlsx` 以及格式参考文件
`/private/tmp/pricing-reference-render/reference.xlsx` 仅作为视觉/字段参考，均为仓库外部临时路径，
不纳入仓库；其中旧文件的字段与 fixture 不一致时，以已提交 fixture 和本报告明确的测试范围为准。
导出始终通过正式 `PricingFileService`。

## POI 验收

- 标题为 `500g香卤大肠头（核价）-LHYC`；材料行从 Excel 第 13 行开始，保留原料工段合并区域。
- 材料值为 `YL-001`、主原料、`100.000`、`82.00%`；领料公式为 `G13/H13`。
- 汇总公式为 `SUM(G13:G13)` 和 `SUM(I13:I13)`；包装数量为 `178`、`178`、`9`、`9`。
- 重量格式规范化后为 `0.000`，利用率为 `0.00%`；公式字符串不含 `#REF!`，Apache POI 计算结果均非错误单元格。
- 打印区域为 `A1:K28`，A4 纵向、适配一页宽、水平居中、未垂直居中、隐藏网格线；关键材料和汇总合并区域存在。

## 视觉验收

LibreOffice 临时 profile 转换后，目标和参考均为 1 页 A4（`595.304 x 841.89 pt`）。初次渲染发现目标继承了 `verticalCentered=true`，在短材料表场景产生了明显顶部空白。已在 `PricingFileService` 中显式关闭垂直居中，并将该要求加入正式布局断言。

修复后的目标截图中，标题区、材料区、汇总区和包材区从页面顶部连续排列，没有跨页、裁切或大段断层；包材区位于汇总后方并仍在打印区域内。headless LibreOffice 环境对中文字体回退不完整，目标和参考截图均有中文缺字；POI 已确认目标工作簿中的中文文本完整，此限制不影响工作簿内容或排版判断。

## 测试结果

- RED：新增“不得垂直居中”打印契约后，`PricingFileServiceTest` 的 4 个正式布局场景均按预期失败，均显示 `print vertically centered` 为 `true`。
- Task 2 回归：`mvn -q -Dtest=PricingFileServiceTest,ReportExportControllerTest,SampleWorkflowControllerTest test` 通过。
- 全量后端：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test` 通过，`156` tests、`0` failures、`0` errors、`0` skipped。
