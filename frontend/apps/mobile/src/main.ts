import { createPinia } from "pinia";
import { createApp } from "vue";
import { showFailToast } from "vant";
import "vant/lib/index.css";
import App from "./App.vue";
import router from "./router";
import { setUnauthorizedHandler } from "./services/api";
import { useAuthStore } from "./stores/auth";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);

app.config.errorHandler = (error) => {
  console.error(error);
  const message = error instanceof Error ? error.message : "页面运行出错";
  showFailToast(message.slice(0, 80));
};

setUnauthorizedHandler(() => {
  const auth = useAuthStore();
  auth.signOut();
  void router.replace({ name: "login" });
});

router.isReady().then(() => {
  app.mount("#app");
});
