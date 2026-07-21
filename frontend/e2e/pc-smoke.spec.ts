import { test, expect } from "@playwright/test";

async function login(page: import("@playwright/test").Page, username = "rnd_assistant") {
  await page.goto("./login");
  await page.getByPlaceholder("例如 rnd_assistant").fill(username);
  await page.getByPlaceholder("默认测试密码 123456").fill("123456");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByTitle("工作台")).toBeVisible({ timeout: 15000 });
}

test("PC login page renders", async ({ page }) => {
  await page.goto("./login");
  await expect(page.getByText("研发样品管理系统登录")).toBeVisible();
  await expect(page.getByRole("button", { name: "登录", exact: true })).toBeVisible();
});

test("PC account login reaches dashboard shell", async ({ page }) => {
  await login(page);
});

test("PC demand list route renders", async ({ page }) => {
  await login(page);
  await page.goto("./demand/list");
  await expect(page.getByText("样品需求列表")).toBeVisible({ timeout: 15000 });
});

test("PC template settings route renders", async ({ page }) => {
  await login(page, "admin");
  await page.goto("./settings/templates");
  await expect(page.getByText("核价模板配置")).toBeVisible({ timeout: 15000 });
});
