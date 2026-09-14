import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "node:path";

export default defineConfig({
  base: "/admin/",
  plugins: [vue()],
  resolve: {
    extensions: [".mjs", ".ts", ".tsx", ".js", ".jsx", ".json"],
    alias: {
      "@": resolve(__dirname, "src"),
      "@rnd/shared": resolve(__dirname, "../../packages/shared/src"),
    },
  },
  server: {
    port: 5173,
    host: "0.0.0.0",
    allowedHosts: true,
    proxy: {
      "/api": {
        target: process.env.RND_BACKEND_URL || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: "127.0.0.1", port: 5183,
    proxy: { "/api": { target: process.env.RND_BACKEND_URL || "http://localhost:8080", changeOrigin: true } },
  },
});
