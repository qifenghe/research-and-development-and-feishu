import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  retries: 0,
  use: {
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "pc-chromium",
      use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:5173/admin/" },
    },
    {
      name: "mobile-chromium",
      use: { ...devices["Pixel 7"], baseURL: "http://127.0.0.1:5174/m/" },
    },
  ],
});
