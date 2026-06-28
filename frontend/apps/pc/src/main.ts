import { createPinia } from "pinia";
import { createApp } from "vue";
import Antd from "ant-design-vue";
import "ant-design-vue/dist/reset.css";
import App from "./App.vue";
import router from "./router";
import { setUnauthorizedHandler } from "./services/api";
import { useAuthStore } from "./stores/auth";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);
app.use(Antd);

setUnauthorizedHandler(() => {
  const auth = useAuthStore();
  auth.signOut();
  router.push({ name: "login" });
});

app.mount("#app");
