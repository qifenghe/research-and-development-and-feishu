import { createPinia } from "pinia";
import { createApp } from "vue";
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

setUnauthorizedHandler(() => {
  const auth = useAuthStore();
  auth.signOut();
  router.push({ name: "login" });
});

app.mount("#app");
