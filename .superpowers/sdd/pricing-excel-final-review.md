# 核价 Excel 格式对齐最终代码审查

审查范围：`1a5e41b..73997c1`，以及设计、实施计划、Task 1-3 报告/审查记录、
`.superpowers/sdd/review-1a5e41b..73997c1.diff`、验收报告和交付 XLSX/PDF/PNG。
本次仅审查，未修改业务代码或交付物，未提交。

## 结论

**Changes requested。** 无 Critical；存在 2 个 Important，因此不能 Approved。

这批变更真实解决了最初的主要格式问题：材料区不再固定留出大段空白，2/25/38/60 条材料均可动态移动汇总和包材区；样式、合并、公式、数字格式和 `A1:K` 打印区域在压力场景中保持完整；短表不再垂直居中；交付文件可由 POI 和 LibreOffice 打开并输出单页 A4。阻塞项集中在模板仍含产品特定/公司主体错误的可见文字，不是动态排版机制本身。

## Critical

无。

## Important

### I1. 所谓脱敏通用模板仍硬编码“肥肠”，并且偏离正式参考的通用汇总文案

`backend/src/main/java/com/lhr/rnd/service/PricingFileService.java:303` 和 `:307` 对所有产品写入
`研发部参考肥肠出成(kg）`、`肥肠得率（%）`。模板 `xl/sharedStrings.xml` 也保存了相同文字。
正式参考文件对应文字是 `研发部参考出成(kg）` 和 `原料得率（%）`。因此清炖牛腩、烤肠等任意非肥肠产品导出时都会显示错误品类，既不是真正的通用脱敏模板，也没有对齐最高优先级参考。

测试反而固化了错误：`backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java:214`、`:219`
要求“肥肠”文案；`PricingFileServiceTest.java:28-32` 的脱敏 token 清单包含“大肠”却漏掉“肥肠”，所以元数据/可见文本扫描无法发现该泄漏。

应把模板和服务文案统一为参考文件的通用措辞，并增加一个非肥肠产品回归，明确拒绝产品特定残留词。

### I2. 保密说明使用的公司主体与表头和正式参考不一致

模板表头和正式参考均为 `江西龙汇肉制品有限责任公司`，但模板保密说明写成
`江西龙汇食品有限公司`；`backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java:23`
还把后者固化为必须存在的 footer marker。生成文件因此在同一页内出现两个不同法律主体，且未按设计保留正式参考的保密说明。

这是用户可见且可能用于正式流转的声明内容，不能视为纯视觉差异。应确认正确公司全称后，让表头、保密说明、模板和断言使用同一主体。

## Minor

### M1. 持久化测试没有覆盖超过模板原 37 行容量的材料数

提交的正式布局用例覆盖 2 条和 25 条，交付 fixture 覆盖 1 条；没有保留 38/60 条回归。
`PricingWorkbookAssertions.assertMaterialRows` 还按实际行号读取模板基线，超过第 37 个材料行后会读到模板汇总区或空行，不能直接承担该边界测试。

本次临时独立探针已验证 1/38/60 条均能正确生成，60 条可渲染为单页 A4，因此这是测试耐久性缺口，不是当前运行时故障。建议新增超过模板容量的固定回归，并让新增材料行统一与首个材料样板行比较样式。

### M2. 工作表标题中的核价标记位置仍与正式参考不同

正式参考标题为 `产品名-LHYC（核价）`，而
`backend/src/main/java/com/lhr/rnd/service/PricingFileService.java:464` 生成 `产品名（核价）-LHYC`；
`PricingFileServiceTest.java:67`、`:137` 固化了后者。文件名兼容性不受影响，但设计把标题列为对齐对象，验收报告不应把当前顺序描述成已与参考一致。

## 已验证

- 动态材料：仓库用例 2/25 条通过；独立临时探针 1/38/60 条通过。38/60 已超过模板原 37 行材料容量。
- 结构与计算：材料行连续；工段和物料名单元格合并存在；汇总、参考出成、得率、参考包数和包材区按材料数移动；`SUM(G...)`、`SUM(I...)`、`G/H`、得率公式引用正确且 POI 求值无错误。
- 格式：重量 `0.000`、利用率/得率 `0.00%`、数量 `0`；动态复制行保留边框、字体、填充、对齐和行高。
- 打印：A4 纵向、fit width 1、水平居中、关闭垂直居中和网格线、打印区域严格为 `A1:K<保密说明行>`。新生成 60 条材料 PDF 为 1 页 A4，无裁切或跨页。
- 用户指出的视觉问题：最终 PNG 相比旧 PNG 消除了短表垂直居中造成的大顶部空白；标题、材料、汇总和包材区连续排列。
- 模板包清理：无隐藏工作表、外链、customXml、自定义属性、破损 defined name；生成文件仅保留 Print_Area。I1/I2 是仍留在可见模板正文中的内容问题。
- 交付可打开：XLSX `unzip -t` 无错误；LibreOffice fresh profile 转换成功，结果为 1 页 A4；交付 XLSX 解包内容与当前 `PricingFileServiceTest` 重新生成文件逐项一致（ZIP 容器时间戳不同，包内文件无差异）。
- 聚焦回归：`PricingFileServiceTest` 6、`ReportExportControllerTest` 2、`SampleWorkflowControllerTest` 78，全部 0 failures / 0 errors。
- 全量后端：`mvn test`，156 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。
- `git diff --check 1a5e41b..73997c1` 通过。

## 最终判定

动态布局、样式复制、公式、数字格式、A4 打印、元数据包清理、API/审核回归和交付可打开性均通过最终验证；但 I1 和 I2 会让正式导出显示错误的产品类别和公司主体。修复并补充针对性回归后再做最终 Approved 复审。

---

## Final Re-review (2026-07-13, commit `e0b5ea8`)

复核 `pricing-excel-final-review.md`、最新验收报告、最新交付 XLSX/PNG、正式参考 XLSX/PNG，以及提交 `e0b5ea8`。本次仅追加审查结论，未修改业务代码或交付物，未提交。

## Critical

无。

## Important

无。原 2 个 Important 与 2 个 Minor 均已关闭：

- I1：模板、服务和断言均改为通用文案 `研发部参考出成(kg）`、`原料得率（%）`；新增非肥肠产品回归，模板与交付 OOXML 均无 `肥肠` 残留。
- I2：表头、保密说明、模板和断言统一为 `江西龙汇肉制品有限责任公司`，交付 OOXML 不再包含错误主体 `江西龙汇食品有限公司`。
- M1：新增固定 60 条材料回归，覆盖第 60 行值、公式、行高、样式、合并区域、动态汇总公式、包材区位置和 `A1:K87` 打印区域。
- M2：工作表标题为 `产品名-LHYC（核价）`，与正式参考文件顺序一致，并保留居中标题样式。

## Verification

- 聚焦回归：`PricingFileServiceTest` 8、`ReportExportControllerTest` 2、`SampleWorkflowControllerTest` 78，共 88 tests，0 failures，0 errors，0 skipped。
- 当前工作区全量后端：158 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
- 最新交付 XLSX `unzip -t` 通过；解包内容与本次测试重新生成 XLSX 逐项一致。
- 公式、数字格式、动态布局、元数据脱敏和打印设置断言继续通过；交付工作簿打印区域为 `A1:K28`，A4 纵向、fit width 1、水平居中、关闭垂直居中和网格线。
- 最新 PDF/PNG 为单页 A4（`595.304 x 841.89 pt`）；视觉复核确认标题、材料、汇总和包材区连续，无裁切、跨页或大段断层。
- 范围外说明：纯 `e0b5ea8` 归档快照受父级已引用、但当前仍未跟踪的非 Excel 依赖文件影响而无法独立编译；该问题并非本提交引入。本结论基于用户指定当前工作区，并已确认本提交涉及的 Excel 文件均与 `e0b5ea8` 一致。

## Conclusion

**Approved**
