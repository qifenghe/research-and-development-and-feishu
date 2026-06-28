import { test, expect } from "@playwright/test";

test("PC login page renders", async ({ page }) => {
  await page.goto("./login");
  await expect(page.getByText("PC 管理后台登录")).toBeVisible();
});

test("PC mock login reaches dashboard shell", async ({ page }) => {
  await page.goto("./login");
  await page.getByRole("button", { name: "MOCK 登录" }).click();
  await expect(page.getByText("工作台")).toBeVisible({ timeout: 15000 });
});

test("PC demand list route renders", async ({ page }) => {
  await page.goto("./demand/list");
  await expect(page.getByText("样品需求列表")).toBeVisible({ timeout: 15000 });
});

test("PC template settings route renders", async ({ page }) => {
  await page.goto("./settings/templates");
  await expect(page.getByText("核价模板配置")).toBeVisible({ timeout: 15000 });
});
