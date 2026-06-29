import { defineStore } from "pinia";
import {
  getAccessToken,
  getStoredUser,
  type SessionPrincipal,
  type UserAccount,
} from "@rnd/shared";
import { api, logout as clearSession, persistLogin } from "../services/api";

export const useAuthStore = defineStore("auth", {
  state: (): {
    token: string | null;
    user: UserAccount | null;
    principal: SessionPrincipal | null;
    loading: boolean;
  } => ({
    token: getAccessToken() as string | null,
    user: getStoredUser<UserAccount>() ?? demoUser(),
    principal: null,
    loading: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    displayName: (state) => state.principal?.name ?? state.user?.name ?? "未登录",
    role: (state) => state.principal?.role ?? state.user?.role ?? "",
  },
  actions: {
    async loginWithFeishuCode(code: string) {
      this.loading = true;
      try {
        const result = await api.session.feishuCallback(code);
        persistLogin(result.accessToken, result.user);
        this.token = result.accessToken;
        this.user = result.user;
        this.principal = null;
      } finally {
        this.loading = false;
      }
    },
    async mockLogin(feishuUserId: string) {
      await this.loginWithFeishuCode(`mock:${feishuUserId}`);
    },
    async fetchMe() {
      if (!this.token) return;
      this.principal = await api.session.me();
    },
    async restoreSession() {
      if (!this.token) return false;
      try {
        await this.fetchMe();
        return true;
      } catch {
        // Safari 偶发网络抖动时，保留本地已登录用户，避免误踢回登录页
        if (this.user?.role) {
          return true;
        }
        this.signOut();
        return false;
      }
    },
    signOut() {
      clearSession();
      this.token = null;
      this.user = null;
      this.principal = null;
    },
  },
});

function demoUser(): UserAccount {
  return {
    id: "USR-DEMO-ENGINEER",
    name: "张研发",
    feishuUserId: "ou_demo_engineer",
    role: "RND_ENGINEER",
    departmentName: "研发部",
    status: "ACTIVE",
    createdAt: "",
    updatedAt: "",
  };
}
