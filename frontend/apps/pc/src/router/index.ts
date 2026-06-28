import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import { useAuthStore } from "../stores/auth";
import AdminLayout from "../layouts/AdminLayout.vue";

const DEMO_BYPASS_AUTH = false;

const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginView.vue"),
    meta: { public: true },
  },
  {
    path: "/login/callback",
    name: "login-callback",
    component: () => import("../views/LoginCallbackView.vue"),
    meta: { public: true },
  },
  {
    path: "/",
    component: AdminLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", name: "dashboard", component: () => import("../views/DashboardView.vue") },
      { path: "flowchart", name: "flowchart", component: () => import("../views/FlowchartView.vue") },
      { path: "demand", name: "demand-module", component: () => import("../views/demand/DemandModuleView.vue") },
      { path: "demand/new", name: "request-new", component: () => import("../views/demand/RequestNewView.vue") },
      { path: "demand/list", name: "demand-list", component: () => import("../views/demand/DemandListView.vue") },
      { path: "demand/:id", name: "demand-detail", component: () => import("../views/demand/DemandDetailView.vue") },
      { path: "demand/review", name: "request-review", component: () => import("../views/demand/RequestReviewView.vue") },
      { path: "rnd", name: "rnd-module", component: () => import("../views/rnd/RndModuleView.vue") },
      { path: "rnd/pool", name: "task-pool", component: () => import("../views/rnd/TaskPoolView.vue") },
      { path: "rnd/assign", name: "task-assign", component: () => import("../views/rnd/TaskAssignView.vue") },
      { path: "rnd/tasks/:id", name: "task-detail", component: () => import("../views/rnd/TaskDetailView.vue") },
      { path: "rnd/stopped", name: "stopped", component: () => import("../views/rnd/StoppedProjectsView.vue") },
      { path: "rnd/history/:versionId", name: "experiment-history", component: () => import("../views/rnd/ExperimentHistoryView.vue") },
      { path: "shipment", name: "shipment-pricing-module", component: () => import("../views/shipment/ShipmentPricingModuleView.vue") },
      { path: "shipment/list", name: "shipment-list", component: () => import("../views/shipment/ShipmentListView.vue") },
      { path: "shipment/:id", name: "shipment-detail", component: () => import("../views/shipment/ShipmentDetailView.vue") },
      { path: "pricing/list", name: "pricing-list", component: () => import("../views/shipment/PricingListView.vue") },
      { path: "pricing/:id", name: "pricing-detail", component: () => import("../views/shipment/PricingDetailView.vue") },
      { path: "finance", name: "finance", component: () => import("../views/shipment/FinanceView.vue") },
      { path: "archive", name: "archive", component: () => import("../views/shipment/ArchiveView.vue") },
      { path: "settings", name: "settings-module", component: () => import("../views/settings/SettingsModuleView.vue") },
      { path: "settings/users", name: "config", component: () => import("../views/settings/UsersSettingsView.vue") },
      { path: "settings/form-fields", name: "form-config", component: () => import("../views/settings/FormFieldsSettingsView.vue") },
      { path: "settings/workflows", name: "workflow-config", component: () => import("../views/settings/WorkflowSettingsView.vue") },
      { path: "settings/dictionaries", name: "dictionary-config", component: () => import("../views/settings/DictionarySettingsView.vue") },
      { path: "settings/templates", name: "template-config", component: () => import("../views/settings/TemplateSettingsView.vue") },
      { path: "settings/role-permissions", name: "role-permissions", component: () => import("../views/settings/RolePermissionsSettingsView.vue") },
      { path: "settings/data-models", name: "data-models", component: () => import("../views/settings/DataModelsView.vue") },
      { path: "403", name: "forbidden", component: () => import("../views/ForbiddenView.vue"), meta: { public: true } },
    ],
  },
];

const router = createRouter({
  history: createWebHistory("/admin/"),
  routes,
});

router.beforeEach(async (to) => {
  if (DEMO_BYPASS_AUTH) {
    if (to.name === "login" || to.name === "login-callback") {
      return { name: "dashboard" };
    }
    return true;
  }
  if (typeof to.query.code === "string" && to.name !== "login-callback") {
    return {
      name: "login-callback",
      query: {
        code: to.query.code,
        redirect: to.fullPath.split("?")[0],
      },
    };
  }

  const auth = useAuthStore();
  if (to.meta.public) return true;
  if (!auth.isAuthenticated) {
    const restored = await auth.restoreSession();
    if (!restored) {
      return { name: "login", query: { redirect: to.fullPath } };
    }
  }
  return true;
});

export default router;
