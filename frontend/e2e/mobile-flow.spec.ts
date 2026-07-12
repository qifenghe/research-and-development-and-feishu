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

test("Mobile office shell keeps the login, dashboard, and fixed action contracts compact", async ({ page }) => {
  await page.goto("./login");
  await expect(page.getByTestId("login-office-shell")).toBeVisible();
  await expect(page.locator('input[name="username"]')).toBeInViewport();
  await expect(page.locator('input[name="password"]')).toBeInViewport();
  await expect(page.getByRole("button", { name: "登录", exact: true })).toBeInViewport();

  await page.getByPlaceholder("例如 rnd_assistant").fill("rnd_assistant");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByText("我的待办")).toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId("todo-summary")).toHaveCount(3);
  await expect(page.getByTestId("todo-priority-task")).toHaveCount(1);
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= window.innerWidth)).toBe(true);
});
