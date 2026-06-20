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

for (const marker of ["dashboardOverview", "GET /api/v1/dashboard/overview", "recentTasks", "pendingPricingFiles"]) {
  if (!js.includes(marker) && !html.includes(marker)) {
    throw new Error(`Missing dashboard-overview marker: ${marker}`);
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

for (const marker of ["GET /api/v1/sample-projects/stopped", "停止原因", "复制为新需求"]) {
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
