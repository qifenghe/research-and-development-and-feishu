import { test, expect } from "@playwright/test";

test("Mobile login page renders", async ({ page }) => {
  await page.goto("./login");
  await expect(page.getByText("手机端登录")).toBeVisible();
});

test("Mobile mock login reaches todo tab with 3 tabs", async ({ page }) => {
  await page.goto("./login");
  await page.getByRole("button", { name: "MOCK 登录" }).click();
  await expect(page.getByText("我的待办")).toBeVisible({ timeout: 15000 });
  await expect(page.getByText("待办")).toBeVisible();
  await expect(page.getByText("样品")).toBeVisible();
  await expect(page.getByText("我的")).toBeVisible();
  await expect(page.getByText("录需求")).toHaveCount(0);
});

test("Mobile samples tab loads", async ({ page }) => {
  await page.goto("./login");
  await page.getByRole("button", { name: "MOCK 登录" }).click();
  await page.getByText("样品").click();
  await expect(page.getByPlaceholder("搜索产品名称 / 样品编号")).toBeVisible({ timeout: 15000 });
});
