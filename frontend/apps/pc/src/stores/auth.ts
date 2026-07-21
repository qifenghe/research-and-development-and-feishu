import { defineStore } from "pinia";
import {
  getAccessToken,
  getStoredUser,
  type SessionPrincipal,
  type UserAccount,
} from "@rnd/shared";
import { api, logout as clearSession, persistLogin } from "../services/api";

interface AuthState {
  token: string | null;
  user: UserAccount | null;
  principal: SessionPrincipal | null;
  loading: boolean;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: getAccessToken(),
    user: getStoredUser<UserAccount>(),
    principal: null,
    loading: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    displayName: (state) => state.principal?.name ?? state.user?.name ?? "未登录",
    role: (state) => state.principal?.role ?? state.user?.role ?? "",
  },
  actions: {
    async loginWithPassword(username: string, password: string) {
      this.loading = true;
      try {
        const result = await api.session.login(username, password);
        persistLogin(result.accessToken, result.user);
        this.token = result.accessToken;
        this.user = result.user;
        await this.fetchMe();
      } finally {
        this.loading = false;
      }
    },
    async loginWithFeishuCode(code: string) {
      this.loading = true;
      try {
        const result = await api.session.feishuCallback(code);
        persistLogin(result.accessToken, result.user);
        this.token = result.accessToken;
        this.user = result.user;
        await this.fetchMe();
      } finally {
        this.loading = false;
      }
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
