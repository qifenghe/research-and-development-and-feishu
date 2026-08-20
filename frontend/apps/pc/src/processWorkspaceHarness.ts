import { createApp } from "vue";
import Antd from "ant-design-vue";
import "ant-design-vue/dist/reset.css";
import ProcessWorkspaceHarness from "./ProcessWorkspaceHarness.vue";

const app = createApp(ProcessWorkspaceHarness);
app.config.errorHandler = error => {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  document.body.dataset.harnessError = message;
};
app.use(Antd).mount("#app");
