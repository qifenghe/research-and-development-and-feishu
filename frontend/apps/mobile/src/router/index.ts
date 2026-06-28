import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useAuthStore } from "../stores/auth";
import MobileLayout from "../layouts/MobileLayout.vue";

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
    component: MobileLayout,
    children: [
      { path: "", redirect: "/todo" },
      { path: "todo", name: "todo", component: () => import("../views/TodoView.vue"), meta: { tab: "todo" } },
      { path: "request/new", name: "request-new", component: () => import("../views/RequestNewView.vue") },
      { path: "requests/review", name: "request-review", component: () => import("../views/RequestReviewView.vue") },
      { path: "tasks/assign", name: "task-assign", component: () => import("../views/TaskAssignView.vue") },
      { path: "samples", name: "samples", component: () => import("../views/SamplesView.vue"), meta: { tab: "samples" } },
      { path: "profile", name: "profile", component: () => import("../views/ProfileView.vue"), meta: { tab: "profile" } },
      { path: "tasks/:id", name: "task-detail", component: () => import("../views/TaskDetailView.vue") },
      { path: "experiments/:id", name: "experiment-form", component: () => import("../views/ExperimentFormView.vue") },
      { path: "tests/:id", name: "test-confirm", component: () => import("../views/TestConfirmView.vue") },
      { path: "shipments/:id", name: "shipment-feedback", component: () => import("../views/ShipmentFeedbackView.vue") },
      { path: "samples/:versionId/history", name: "sample-history", component: () => import("../views/SampleHistoryView.vue") },
    ],
  },
];

const router = createRouter({
  history: createWebHistory("/m/"),
  routes,
});

router.beforeEach(async (to) => {
  if (DEMO_BYPASS_AUTH) {
    if (to.name === "login" || to.name === "login-callback") {
      return { name: "todo" };
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
      return { name: "login" };
    }
  }
  return true;
});

export default router;
