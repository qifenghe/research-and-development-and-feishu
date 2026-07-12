#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const pcRoutes = [
  "dashboard",
  "flowchart",
  "demand-module",
  "demand-list",
  "demand-detail",
  "request-new",
  "request-review",
  "rnd-module",
  "task-pool",
  "my-tasks",
  "pending-tests",
  "task-assign",
  "task-detail",
  "stopped",
  "experiment-history",
  "shipment-pricing-module",
  "shipment-list",
  "shipment-record",
  "shipment-detail",
  "pricing-list",
  "pricing-detail",
  "finance",
  "archive",
  "settings-module",
  "config",
  "form-config",
  "workflow-config",
  "dictionary-config",
  "template-config",
  "data-models",
];

const mobileRoutes = [
  "todo",
  "samples",
  "profile",
  "task-detail",
  "experiment-form",
  "test-confirm",
  "shipment-feedback",
  "shipment-record",
  "pricing-list",
  "pricing-detail",
  "task-customer-feedback",
  "request-review",
  "task-assign",
  "sample-history",
  "forbidden",
];

function read(file) {
  return fs.readFileSync(path.join(root, file), "utf8");
}

const pcRouter = read("apps/pc/src/router/index.ts");
const mobileRouter = read("apps/mobile/src/router/index.ts");

for (const route of pcRoutes) {
  if (!pcRouter.includes(`name: "${route}"`) && !pcRouter.includes(`name:'${route}'`)) {
    throw new Error(`Missing PC route: ${route}`);
  }
}

for (const route of mobileRoutes) {
  if (!mobileRouter.includes(`name: "${route}"`) && !mobileRouter.includes(`name:'${route}'`)) {
    throw new Error(`Missing mobile route: ${route}`);
  }
}

console.log(
  `Frontend route check passed: ${pcRoutes.length} PC routes, ${mobileRoutes.length} mobile routes.`,
);
