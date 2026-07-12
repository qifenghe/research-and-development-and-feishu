import { expect, test, type Page } from "@playwright/test";

const PASSWORD = "123456";

async function login(page: Page, username: string) {
  await page.goto("./login");
  await page.getByPlaceholder("例如 rnd_assistant").fill(username);
  await page.getByPlaceholder("默认测试密码 123456").fill(PASSWORD);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page.getByRole("heading", { name: "我的待办" })).toBeVisible({ timeout: 15_000 });
}

async function relogin(page: Page, username: string) {
  await page.evaluate(() => localStorage.clear());
  await login(page, username);
}

test("five roles complete request-to-finance pricing handoff", async ({ page }) => {
  test.setTimeout(120_000);
  const suffix = `${Date.now()}`.slice(-8);
  const productName = `闭环验收菜品${suffix}`;

  await login(page, "rnd_assistant");
  await page.goto("./request/new");
  await page.getByPlaceholder("如 500g香卤大肠头").fill(productName);
  await page.getByPlaceholder("如 冷冻即热菜").fill("冷冻即热菜");
  await page.getByPlaceholder("如 LHYC / 业务员客户").fill("闭环验收客户");
  await page.getByPlaceholder("如 500g/袋").fill("500g/袋");
  await page.getByPlaceholder(/如商超零售冷冻即热/).fill("餐饮渠道复热出餐");
  await page.getByPlaceholder(/如香卤、麻辣/).fill("香辣，复热后风味稳定");
  await page.getByRole("button", { name: "提交需求" }).click();
  await expect(page.getByText("需求已提交，等待研发总监审核")).toBeVisible();

  await relogin(page, "rnd_director");
  await page.goto("./requests/review");
  const requestCard = page.locator(".task-card").filter({ hasText: productName });
  await expect(requestCard).toBeVisible();
  await requestCard.getByRole("button", { name: "审核通过" }).click();
  await expect(page.getByText("审核通过，已进入任务池")).toBeVisible();

  await page.goto("./tasks/assign");
  const assignCard = page.locator(".task-card").filter({ hasText: productName });
  await expect(assignCard).toBeVisible();
  await assignCard.getByRole("button", { name: "分配任务" }).click();
  await expect(page.getByText(/任务已分配给/)).toBeVisible();

  await relogin(page, "rnd_engineer");
  const engineerCard = page.locator("a, .task-card").filter({ hasText: productName }).first();
  await expect(engineerCard).toBeVisible();
  await engineerCard.click();
  await page.getByRole("button", { name: "接受任务" }).click();
  await expect(page.getByText("选择本次打样方式")).toBeVisible();
  await page.getByRole("button", { name: /快速打样/ }).click();
  await expect(page.getByText("配方投入", { exact: true })).toBeVisible();
  await page.getByPlaceholder("物料名称").fill("猪肉");
  await page.getByRole("textbox", { name: "重量", exact: true }).fill("10");
  await page.getByRole("button", { name: "+ 添加配料" }).click();
  await page.getByPlaceholder("物料名称").nth(1).fill("香辛料");
  await page.getByRole("textbox", { name: "重量", exact: true }).nth(1).fill("2");
  await page.getByRole("button", { name: "调整工序" }).click();
  await page.getByPlaceholder("填写工序名称").fill("卤制");
  await page.getByRole("button", { name: "确认并开始打样" }).click();
  await page.getByLabel("投入重量").fill("10");
  await page.getByLabel("下一步出成").fill("8.5");
  await page.getByLabel("余料重量").fill("0.5");
  await page.getByLabel("成品重量").fill("8.5");
  await page.getByLabel("成品数量").fill("17");
  await page.getByRole("button", { name: "保存草稿" }).click();
  await expect(page.getByText("草稿已保存").first()).toBeVisible();
  await page.getByRole("button", { name: "通知测试" }).click();
  await expect(page.getByText("已通知内部测试")).toBeVisible();

  await relogin(page, "tester");
  const testCard = page.locator("a, .task-card").filter({ hasText: productName }).first();
  await expect(testCard).toBeVisible();
  await testCard.click();
  await page.getByPlaceholder("微波 / 水浴").fill("微波");
  await page.getByPlaceholder("8.5 / 10").fill("9");
  await page.getByPlaceholder("鸡肉嫩度合适").fill("口感符合要求");
  await page.getByRole("button", { name: "测试通过并锁版" }).click();
  await expect(page.getByText("测试通过，版本已锁定")).toBeVisible();

  await relogin(page, "rnd_assistant");
  const pricingReadyCard = page.locator("a, .task-card").filter({ hasText: productName }).first();
  await expect(pricingReadyCard).toBeVisible();
  await pricingReadyCard.click();
  await page.getByRole("button", { name: "生成核价 Excel" }).click();
  await expect(page.locator(".status-badge").filter({ hasText: "待核价审核" })).toBeVisible();

  await relogin(page, "finance");
  await page.goto("./pricing");
  await expect(page.getByText(productName)).toHaveCount(0);

  await relogin(page, "rnd_engineer");
  await page.goto("./pricing");
  const pendingReviewCard = page.locator("a, .task-card").filter({ hasText: productName }).first();
  await expect(pendingReviewCard).toBeVisible();
  await pendingReviewCard.click();
  await page.getByRole("button", { name: "审核通过" }).click();
  await expect(page.locator(".status-badge").filter({ hasText: "审核通过" })).toBeVisible();
  const approvedPricingUrl = page.url();

  await relogin(page, "rnd_assistant");
  await page.goto(approvedPricingUrl);
  await expect(page.getByRole("button", { name: "通知财务" })).toBeVisible();
  await page.getByRole("button", { name: "通知财务" }).click();
  await expect(page.getByText("已通知财务")).toBeVisible();

  await relogin(page, "finance");
  await page.goto("./pricing");
  const financeCard = page.locator("a, .task-card").filter({ hasText: productName }).first();
  await expect(financeCard).toBeVisible();
  await financeCard.click();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "下载核价文件" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.xlsx$/);
  await page.getByRole("button", { name: "确认接收" }).click();
  await expect(page.getByText("已确认接收")).toBeVisible();
});
