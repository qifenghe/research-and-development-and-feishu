import { createMemoryHistory, createRouter } from "vue-router";

const emptyRouteComponent = { template: "<span />" };

export function createProcessWorkspaceHarnessRouter() {
  return createRouter({
    history: createMemoryHistory("/admin/"),
    routes: [
      {
        path: "/rnd/tasks/:id/experiment",
        name: "process-workspace-experiment",
        component: emptyRouteComponent,
      },
      {
        path: "/:pathMatch(.*)*",
        name: "process-workspace-fallback",
        component: emptyRouteComponent,
      },
    ],
  });
}
