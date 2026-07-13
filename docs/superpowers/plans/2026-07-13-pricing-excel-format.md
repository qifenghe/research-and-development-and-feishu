# 核价 Excel 格式对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让系统生成的核价 Excel 在标题、材料表、汇总、包材和打印预览上匹配用户提供的正式参考文件，并随实际材料数量紧凑排版。

**Architecture:** 使用一份脱敏的正式核价模板作为样式源；`PricingFileService` 只复制模板行、移动结构区并填充数据，不再删除带样式单元格。行位置通过实际材料数量动态计算，所有汇总公式和打印区域都基于最终行号生成。

**Tech Stack:** Java 17、Spring Boot 3、Apache POI、JUnit 5、AssertJ、LibreOffice headless/PDF render。

## Global Constraints

- 参考格式以用户提供的正常核价文件为最高优先级。
- 不改变核价版本、审核、财务移交和下载权限流程。
- 不改变现有 REST API 和文件命名规则。
- 重量使用 `0.000`，百分比使用 `0.00%`，数量使用 `0`，日期使用 `yyyy.MM.dd`。
- A4 纵向打印，宽度一页，隐藏网格线，内容不得断层或裁切。
- 工作区存在其他未提交修改，只暂存本计划涉及的文件。

---

### Task 1: 锁定正式模板与动态布局契约

**Files:**
- Modify: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`
- Create: `backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java`

**Interfaces:**
- Consumes: `PricingFileService.generate(SampleVersion, String, String)`。
- Produces: 动态布局断言工具 `PricingWorkbookAssertions.assertFormalLayout(Workbook, int)`，供后续任务验证模板与输出。

- [ ] **Step 1: 为少量材料写失败测试**

测试生成 2 条材料的文件，并断言：材料表从模板首行连续排列；汇总行紧随第 2 条材料；汇总和包材之间不存在超过 4 行的空白；标题单元格样式、材料边框、利用率数字格式、打印区域均存在。

```java
try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
    var sheet = workbook.getSheetAt(0);
    PricingWorkbookAssertions.assertFormalLayout(workbook, 2);
    assertThat(sheet.getRow(13).getCell(7).getCellStyle().getDataFormatString())
            .isEqualTo("0.00%");
}
```

- [ ] **Step 2: 为较多材料写失败测试**

生成 25 条材料，断言材料行完整、汇总公式引用第 1 到第 25 条材料、包材区被向下移动且仍位于打印区域内。

```java
assertThat(summaryRow.getCell(6).getCellFormula())
        .isEqualTo("SUM(G13:G37)");
assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -Dtest=PricingFileServiceTest test
```

Expected: FAIL，现有固定汇总行和缺失样式不满足断言。

- [ ] **Step 4: 提交测试契约**

```bash
git add backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java \
  backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java
git commit -m "test: define formal pricing workbook layout"
```

---

### Task 2: 重建正式模板并实现动态行生成

**Files:**
- Modify: `backend/src/main/resources/templates/pricing-material-list-template.xlsx`
- Modify: `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`
- Test: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`

**Interfaces:**
- Consumes: `SampleVersion.materials()`、`referenceOutputKg()`、`unitWeightKg()`、产品标题字段。
- Produces: 紧凑单页 `.xlsx` 字节流；新增包内方法 `LayoutRows layoutRows(int materialCount)`，返回材料末行、汇总起始行和包材起始行。

- [ ] **Step 1: 建立脱敏正式模板**

从参考文件复制视觉结构，清除产品、物料和数量内容，仅保留：标题结构、材料表头与样板行、汇总样板、包材表头与样板行、保密说明、列宽、行高、合并区域、打印设置。

- [ ] **Step 2: 实现完整行复制工具**

在 `PricingFileService` 中增加行复制逻辑，复制单元格类型、样式、公式、批注、行高和合并区域；不得调用 `row.removeCell` 清空模板格式。

```java
private void copyTemplateRow(Sheet sheet, int sourceRowIndex, int targetRowIndex) {
    var source = sheet.getRow(sourceRowIndex);
    var target = sheet.getRow(targetRowIndex) == null
            ? sheet.createRow(targetRowIndex)
            : sheet.getRow(targetRowIndex);
    target.setHeight(source.getHeight());
    for (int col = 0; col < source.getLastCellNum(); col++) {
        var sourceCell = source.getCell(col);
        var targetCell = target.getCell(col) == null ? target.createCell(col) : target.getCell(col);
        if (sourceCell != null) {
            targetCell.setCellStyle(sourceCell.getCellStyle());
        }
    }
}
```

- [ ] **Step 3: 实现动态布局行号**

材料数据从首个材料样板行开始；汇总行、参考出成、得率、参考包数和包材区基于材料数计算，最少保留 1 个材料样板行。

```java
record LayoutRows(
        int lastMaterialRow,
        int totalRow,
        int referenceOutputRow,
        int yieldRateRow,
        int packageCountRow,
        int packagingStartRow
) {}
```

- [ ] **Step 4: 填充数据且保持数字格式**

重量写入数值并使用 `0.000`；利用率以 `0.95` 形式写入并使用 `0.00%`；领料重量为公式 `G/H`；得率为参考出成除主原料领料重量。

- [ ] **Step 5: 动态更新合并区域和打印区域**

移动汇总与包材结构后，重建受影响合并区域；设置 A4 纵向、`fitToPage`、`fitWidth=1`、水平居中、隐藏网格线，并将打印区域结束行设为包材区末行。

- [ ] **Step 6: 运行聚焦测试**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -Dtest=PricingFileServiceTest test
```

Expected: PASS。

- [ ] **Step 7: 运行核价相关回归测试**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -Dtest=PricingFileServiceTest,ReportExportControllerTest,SampleWorkflowControllerTest test
```

Expected: PASS，文件生成、下载和审核流程无回归。

- [ ] **Step 8: 提交实现**

```bash
git add backend/src/main/resources/templates/pricing-material-list-template.xlsx \
  backend/src/main/java/com/lhr/rnd/service/PricingFileService.java \
  backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java
git commit -m "fix: align pricing workbook with formal template"
```

---

### Task 3: 真实文件重生成与视觉验收

**Files:**
- Create: `backend/src/test/java/com/lhr/rnd/service/PricingWorkbookFixture.java`
- Create: `outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx`
- Create: `docs/superpowers/reports/2026-07-13-pricing-excel-format-verification.md`

**Interfaces:**
- Consumes: Task 2 的 `PricingFileService` 和真实香卤大肠头测试数据。
- Produces: 用户可直接打开的验收 `.xlsx` 文件及渲染检查记录。

- [ ] **Step 1: 用真实数据生成验收文件**

使用现有香卤大肠头样品数据或第二份文件中的材料数据构建 `SampleVersion`，调用正式服务生成文件，保存到 `outputs/pricing-format-review/`。

- [ ] **Step 2: 检查值、公式和格式**

使用 Apache POI 检查标题、材料行、汇总公式、数字格式、合并区域和打印区域；确认不存在 `#REF!` 公式。

- [ ] **Step 3: 渲染为 PDF/PNG**

Run:

```bash
soffice --headless --convert-to pdf \
  --outdir outputs/pricing-format-review \
  'outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx'
```

Expected: 生成单页 PDF，标题、材料表、汇总和包材区连续，无大段空白。

- [ ] **Step 4: 与参考文件并排目检**

记录页面尺寸、标题层级、材料表密度、汇总位置、包材区位置和裁切情况。任何明显断层、无边框单元格或错误百分比必须返回 Task 2 修复。

- [ ] **Step 5: 运行后端全量测试**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test
```

Expected: 全部通过。

- [ ] **Step 6: 提交验收资料**

```bash
git add backend/src/test/java/com/lhr/rnd/service/PricingWorkbookFixture.java \
  docs/superpowers/reports/2026-07-13-pricing-excel-format-verification.md
git commit -m "test: verify formal pricing workbook output"
```

验收 `.xlsx` 作为用户交付物保存在 `outputs/pricing-format-review/`，不强制加入 Git。
