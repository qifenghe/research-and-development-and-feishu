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
const mobileRouter = read("apps/mobile/src/router/index.ts");
const mobileProfile = read("apps/mobile/src/views/ProfileView.vue");
const mobileAuthStore = read("apps/mobile/src/stores/auth.ts");
const sessionApi = read("packages/shared/src/api/session.ts");
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

if (!mobileLogin.includes('name="username"') || !mobileLogin.includes('name="password"')) {
  throw new Error("Mobile web login must collect username and password");
}

if (!mobileLogin.includes("auth.loginWithPassword") || !mobileAuthStore.includes("loginWithPassword")) {
  throw new Error("Mobile login must use the web account authentication store");
}

if (!sessionApi.includes('client.post<AuthLoginResult>("/auth/login"')) {
  throw new Error("Shared session API must call POST /auth/login");
}

if (mobileLogin.includes("requestAccess") || mobileLogin.includes("trycloudflare.com")) {
  throw new Error("Mobile web login must not expose Feishu tunnel diagnostics in the primary login form");
}

if (mobileLogin.includes("飞书") || !mobileLogin.includes("van-collapse")) {
  throw new Error("Mobile web login must hide Feishu entry points and collapse test helpers by default");
}

if (!mobileLogin.includes("location.host") || !mobileLogin.includes("登录已过期")) {
  throw new Error("Mobile web login must provide host-aware network errors and an expired-session message");
}

if (mobileRouter.includes("to.query.code")) {
  throw new Error("Mobile router must not auto-redirect H5 query codes to the Feishu callback");
}

if (mobileProfile.includes("飞书已绑定")) {
  throw new Error("Mobile profile must not present Feishu binding as an H5 login requirement");
}

console.log("Frontend API contract check passed: experiment, shipment, and web login contracts match backend DTOs.");
