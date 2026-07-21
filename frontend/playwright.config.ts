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
      testMatch: /pc-.*\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        channel: "chrome",
        baseURL: "http://127.0.0.1:5173/admin/",
      },
    },
    {
      name: "mobile-chromium",
      testMatch: /mobile-.*\.spec\.ts/,
      use: {
        ...devices["Pixel 7"],
        channel: "chrome",
        baseURL: process.env.MOBILE_BASE_URL ?? "http://127.0.0.1:5174/m/",
      },
    },
  ],
});
