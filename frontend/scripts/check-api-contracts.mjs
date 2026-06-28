#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(file) {
  return fs.readFileSync(path.join(root, file), "utf8");
}

const taskApi = read("packages/shared/src/api/task.ts");
const sharedTypes = read("packages/shared/src/types/index.ts");
const experimentForm = read("apps/mobile/src/views/ExperimentFormView.vue");
const mobileLogin = read("apps/mobile/src/views/LoginView.vue");
const shipmentDetail = read("apps/pc/src/views/shipment/ShipmentDetailView.vue");
const draftPayloadBlock = taskApi.match(/export interface SaveExperimentDraftPayload \{[\s\S]*?\n\}/)?.[0] ?? "";
const materialTypeBlock = sharedTypes.match(/export interface ExperimentMaterial \{[\s\S]*?\n\}/)?.[0] ?? "";
const buildMaterialsBlock = experimentForm.match(/function buildMaterials\(\): ExperimentMaterial\[] \{[\s\S]*?\n\}/)?.[0] ?? "";

const requiredMaterialFields = [
  "stage",
  "sequence",
  "materialCode",
  "materialName",
  "weightKg",
  "utilizationRate",
  "remark",
];

for (const field of requiredMaterialFields) {
  if (!materialTypeBlock.includes(`${field}`) || !draftPayloadBlock.includes(`${field}`)) {
    throw new Error(`Experiment material contract is missing backend field: ${field}`);
  }
}

const forbiddenPayloadFields = [
  "name:",
  "category:",
  "quantity:",
  "unit:",
  "lossRate:",
];

for (const field of forbiddenPayloadFields) {
  if (buildMaterialsBlock.includes(field) || draftPayloadBlock.includes(field) || materialTypeBlock.includes(field)) {
    throw new Error(`Experiment material contract still contains legacy payload field: ${field}`);
  }
}

if (!shipmentDetail.includes("feedbackBy: auth.displayName")) {
  throw new Error("Customer feedback payload must send backend field: feedbackBy");
}

if (shipmentDetail.includes("operatorName: auth.displayName")) {
  throw new Error("Customer feedback payload still contains unsupported field: operatorName");
}

if (!mobileLogin.includes("build=official-requestAccess-v3")) {
  throw new Error("Mobile Feishu login diagnostics must expose the current requestAccess build version");
}

if (!mobileLogin.includes("H5 可信域名") || !mobileLogin.includes("trycloudflare.com")) {
  throw new Error("Mobile Feishu login must explain trusted-domain failures for Cloudflare tunnel URLs");
}

if (mobileLogin.includes("Boolean(window.tt || window.h5sdk")) {
  throw new Error("Mobile Feishu login must not treat SDK/polyfill presence as real Feishu WebView capability");
}

console.log("Frontend API contract check passed: experiment and shipment payloads match backend DTOs.");
