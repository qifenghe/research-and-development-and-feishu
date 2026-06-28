import { test, expect } from "@playwright/test";

test("Mobile navigates to samples after login", async ({ page }) => {
  await page.goto("./login");
  await page.getByRole("button", { name: "MOCK 登录" }).click();
  await page.getByText("样品").click();
  await expect(page.getByText("样品", { exact: true }).first()).toBeVisible({ timeout: 15000 });
  await expect(page.getByText("全部")).toBeVisible();
});

test("Mobile profile tab renders", async ({ page }) => {
  await page.goto("./login");
  await page.getByRole("button", { name: "MOCK 登录" }).click();
  await page.getByText("我的").click();
  await expect(page.getByText("我的", { exact: true }).first()).toBeVisible({ timeout: 15000 });
});
