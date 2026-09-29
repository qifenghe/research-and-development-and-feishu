import { test, expect } from '@playwright/test';

test('admin brand and account controls stay inside header', async ({ page }) => {
  await page.goto(`${process.env.PC_BASE_URL ?? 'http://127.0.0.1:5193'}/admin/login`);
  await page.getByLabel('账号', { exact: true }).fill('admin');
  await page.getByLabel('密码', { exact: true }).fill('123456');
  await page.getByRole('button', { name: /登.*录/ }).click();
  await expect(page.locator('.topbar')).toBeVisible();
  for (const width of [1280, 768]) {
    await page.setViewportSize({ width, height: 900 });
    const header = await page.locator('.topbar').boundingBox();
    const title = await page.locator('.brand-title').boundingBox();
    const subtitle = await page.locator('.brand-sub').boundingBox();
    const actions = await page.locator('.topbar-actions').boundingBox();
    expect(title!.y).toBeGreaterThanOrEqual(header!.y);
    expect(subtitle!.y + subtitle!.height).toBeLessThanOrEqual(header!.y + header!.height);
    expect(title!.y + title!.height).toBeLessThanOrEqual(subtitle!.y);
    expect(title!.x + title!.width).toBeLessThanOrEqual(actions!.x);
  }
});
