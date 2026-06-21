import fs from "node:fs";

const html = fs.readFileSync(new URL("./index.html", import.meta.url), "utf8");
const js = fs.readFileSync(new URL("./app.js", import.meta.url), "utf8");

const requiredViews = [
  "dashboard",
  "demand-module",
  "request-new",
  "request-review",
  "rnd-module",
  "task-pool",
  "task-assign",
  "mobile-home",
  "experiment",
  "experiment-history",
  "test-config",
  "internal-test",
  "shipment-pricing-module",
  "shipment-list",
  "shipment-detail",
  "pricing-list",
  "pricing-detail",
  "finance",
  "archive",
  "settings-module",
  "data-models",
  "config",
  "form-config",
  "workflow-config",
  "notification-config",
  "template-config",
  "dictionary-config",
  "file-management",
  "flowchart",
  "stopped",
];

for (const view of requiredViews) {
  if (!js.includes(`id: "${view}"`)) {
    throw new Error(`Missing view route: ${view}`);
  }
}

const requiredActions = [
  "#demand-module",
  "#request-new",
  "#request-review",
  "#rnd-module",
  "#task-pool",
  "#task-assign",
  "#mobile-home",
  "#experiment",
  "#experiment-history",
  "#test-config",
  "#internal-test",
  "#shipment-pricing-module",
  "#shipment-list",
  "#shipment-detail",
  "#pricing-list",
  "#pricing-detail",
  "#finance",
  "#archive",
  "#settings-module",
  "#data-models",
  "#config",
  "#form-config",
  "#workflow-config",
  "#notification-config",
  "#template-config",
  "#dictionary-config",
  "#file-management",
  "#flowchart",
  "#stopped",
];

for (const href of requiredActions) {
  const route = href.slice(1);
  if (!js.includes(href) && !html.includes(href) && !js.includes(`"${route}"`)) {
    throw new Error(`Missing navigation target: ${href}`);
  }
}

if (!html.includes("id=\"app\"")) {
  throw new Error("Missing app mount node");
}

for (const marker of ["search-name", "search-start", "search-end", "filterSampleRecords"]) {
  if (!js.includes(marker)) {
    throw new Error(`Missing search feature marker: ${marker}`);
  }
}

for (const forbiddenMetric of [
  '["待审核需求", "GET /sample-requests?status=PENDING_REVIEW"',
  '["按名称搜索", "keyword"',
  '["任务池待分发", "GET /rnd-tasks?status=PENDING_ASSIGNMENT"',
  '["打样中", "GET /rnd-tasks?status=SAMPLING"',
  '["待内部测试", "GET /rnd-tasks?status=PENDING_TEST"',
  '["待生成核价", "GET /pricing-files?status=GENERATED"',
  '["已通知财务", "GET /pricing-files?status=FINANCE_NOTIFIED"',
]) {
  if (js.includes(forbiddenMetric)) {
    throw new Error(`Developer placeholder leaked into module metric UI: ${forbiddenMetric}`);
  }
}

for (const forbiddenRenderedMarker of [
  "${dashboardOverview.endpoint}",
  "${detailEndpoint}",
  "${pagedListExamples.pricing.endpoint}",
  "${endpoint}",
  ">GET /api",
  "keyword:",
  "sort:",
  "page=0",
  "availableActions",
  "动作编码",
  "<div>接口</div>",
]) {
  if (js.includes(forbiddenRenderedMarker) || html.includes(forbiddenRenderedMarker)) {
    throw new Error(`Developer marker leaked into rendered prototype UI: ${forbiddenRenderedMarker}`);
  }
}

for (const marker of ["dashboardOverview", "数据实时汇总", "recentTasks", "pendingPricingFiles"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing dashboard-overview marker: ${marker}`);
  }
}

for (const marker of ["dashboardDrilldowns", "查看正在打样的任务", "查看待提交财务的核价文件", "查看待审核需求列表"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing drilldown marker: ${marker}`);
  }
}

for (const marker of ["pagedListExamples", "分页：每页10条", "排序：", "pagination-bar"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing paged-list marker: ${marker}`);
  }
}

for (const marker of ["task-detail", "按当前角色展示可处理动作", "按钮按权限显示", "测试通过并锁版"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing task-detail marker: ${marker}`);
  }
}

for (const marker of ["寄样记录与反馈", "客户通过", "生成核价文件"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing shipment-detail marker: ${marker}`);
  }
}

for (const marker of ["核价文件版本与财务通知", "下载核价文件", "通知财务核价"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing pricing-detail marker: ${marker}`);
  }
}

for (const marker of ["config-card", "表单字段配置", "流程状态配置", "飞书通知配置", "模板配置", "基础字典配置"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing config-center marker: ${marker}`);
  }
}

for (const marker of ["dataModelGroups", "SampleRequest", "SampleProject", "SampleVersion", "数据模型确认清单"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing data-model marker: ${marker}`);
  }
}

for (const marker of ["保留原因与历史资料", "停止原因", "复制为新需求"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing stopped-project marker: ${marker}`);
  }
}

const sidebarMatch = js.match(/const sidebarItems = \[([\s\S]*?)\];/);
if (!sidebarMatch) {
  throw new Error("Missing sidebarItems");
}

const sidebarCount = (sidebarMatch[1].match(/\[/g) || []).length;
if (sidebarCount !== 5) {
  throw new Error(`Expected 5 top-level sidebar modules, found ${sidebarCount}`);
}

console.log(`Prototype route check passed: ${requiredViews.length} views, ${requiredActions.length} navigation targets, ${sidebarCount} top-level modules.`);
