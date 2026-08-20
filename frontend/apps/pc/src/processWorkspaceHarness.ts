import { createApp } from "vue";
import Antd from "ant-design-vue";
import { createPinia } from "pinia";
import "ant-design-vue/dist/reset.css";
import ProcessWorkspaceHarness from "./ProcessWorkspaceHarness.vue";
import { createProcessWorkspaceHarnessRouter } from "./processWorkspaceHarnessRouter";
import { useAuthStore } from "./stores/auth";

const scenario = new URLSearchParams(location.search).get("scenario") || "hydration";
const pinia = createPinia();
const router = createProcessWorkspaceHarnessRouter();
await router.push(scenario === "experiment-parent" ? "/rnd/tasks/task-a/experiment" : "/");
await router.isReady();
const auth = useAuthStore(pinia);
auth.principal = {
  userId: "engineer-1",
  name: "研发工程师",
  role: "RND_ENGINEER",
  expiresAt: "2099-01-01T00:00:00Z",
};
const app = createApp(ProcessWorkspaceHarness);
app.config.errorHandler = error => {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  document.body.dataset.harnessError = message;
};
app.use(pinia).use(router).use(Antd).mount("#app");
