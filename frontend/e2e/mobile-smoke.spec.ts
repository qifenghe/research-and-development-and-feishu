import { test, expect } from "@playwright/test";

async function login(page: import("@playwright/test").Page, username = "rnd_assistant") {
  await page.goto("./login");
  await page.getByPlaceholder("例如 rnd_assistant").fill(username);
  const loginRequest = page.waitForRequest((request) => (
    request.method() === "POST" && request.url().includes("/api/v1/auth/login")
  ));
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await loginRequest;
  await expect(page.getByText("我的待办")).toBeVisible({ timeout: 15000 });
}

test("Mobile login page renders", async ({ page }) => {
  await page.goto("./login");
  await expect(page.getByText("研发样品管理")).toBeVisible();
  await expect(page.locator('input[name="username"]')).toBeVisible();
  await expect(page.locator('input[name="password"]')).toBeVisible();
  await expect(page.getByRole("button", { name: "登录", exact: true })).toBeVisible();
  await expect(page.getByText(/飞书|Feishu/i)).toHaveCount(0);
  await expect(page.getByText("测试辅助")).toBeVisible();
  await expect(page.getByText("研发内勤")).toBeHidden();
});

test("Mobile account login reaches todo tab", async ({ page }) => {
  await login(page);
  await expect(page.getByRole("tab", { name: "待办" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "我的" })).toBeVisible();
  await expect(page.getByText("录入需求", { exact: true })).toBeVisible();
});

test("Mobile login does not wait for the optional session refresh", async ({ page }) => {
  await page.route("**/api/v1/session/me", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 5000));
    await route.continue();
  });
  await page.goto("./login");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByText("我的待办")).toBeVisible({ timeout: 2000 });
});

test("Mobile samples tab loads", async ({ page }) => {
  await login(page, "rnd_engineer");
  await page.getByRole("tab", { name: "样品" }).click();
  await expect(page.getByPlaceholder("搜索产品名称 / 样品编号")).toBeVisible({ timeout: 15000 });
});
