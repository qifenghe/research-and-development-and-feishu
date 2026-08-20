import { expect, test, type Route } from "@playwright/test";

const harness = (scenario: string) => `/admin/process-workspace-harness.html?scenario=${scenario}`;

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify({ code: status === 200 ? "0" : String(status), message: status === 200 ? "ok" : "failed", data: body }) });
}

test("server hydration survives Vue batching and cache restoration remains dirty", async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  const saves: ProcessPlanPayload[] = [];
  await page.route("**/experiment-forms/form-hydration/process-plan/revisions", route => fulfillJson(route, []));
  await page.route("**/experiment-forms/form-hydration/process-plan", async route => {
    if (route.request().method() === "PUT") {
      const body = route.request().postDataJSON() as ProcessPlanPayload;
      saves.push(body);
      await fulfillJson(route, { ...body, versionNo: body.versionNo + 1 });
      return;
    }
    await route.continue();
  });
  await page.goto(harness("hydration"));
  await page.waitForTimeout(200);
  expect(pageErrors).toEqual([]);
  await page.getByTestId("accept-server").click();
  await expect(page.getByText("服务端大工序", { exact: true })).toBeVisible();
  await page.getByText("原辅料准备", { exact: true }).dblclick();
  await page.getByRole("button", { name: "保存草稿" }).click();
  await expect.poll(() => saves.length).toBe(1);
  expect(saves[0]?.majorProcesses.map(item => item.processName)).toEqual(["服务端大工序", "原辅料准备"]);

  await page.getByTestId("accept-cache").click();
  await expect(page.getByText("缓存大工序", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "保存草稿" }).click();
  await expect.poll(() => saves.length).toBe(2);
  expect(saves[1]?.majorProcesses.map(item => item.processName)).toEqual(["缓存大工序"]);
});

test("reopening submit after a failed current check cannot reuse the earlier success", async ({ page }) => {
  let checks = 0;
  await page.route("**/experiment-forms/form-submit/process-plan/submission-check", async route => {
    checks++;
    if (checks === 1) await fulfillJson(route, { ready: true, errors: [], warnings: [] });
    else await fulfillJson(route, null, 500);
  });
  await page.goto(harness("submit"));
  await page.getByTestId("open-submit").click();
  await expect(page.getByText("正式提交工艺版本")).toBeVisible();
  await page.getByText("我已核对配方、工艺得率和关键控制点").click();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeEnabled();
  await page.getByTestId("bump-version").click({ force: true });
  await expect(page.getByText("failed")).toBeVisible();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeDisabled();
  await page.getByRole("button", { name: "取消" }).click();
  await page.getByTestId("open-submit").click();
  await expect(page.getByText("failed")).toBeVisible();
  await page.getByText("我已核对配方、工艺得率和关键控制点").click();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeDisabled();
});

test("changing form clears a revision reason and requires a freshly bound diff check", async ({ page }) => {
  const sourcePlan = { versionNo: 1, status: "SUBMITTED", majorProcesses: [], balanceToleranceKg: 0.01 };
  await page.route("**/experiment-forms/form-submit/process-plan/submission-check", route => fulfillJson(route, { ready: true, errors: [], warnings: [] }));
  await page.route("**/experiment-forms/form-submit/process-plan/revisions/source-revision", route => fulfillJson(route, revision("form-submit", sourcePlan)));
  await page.route("**/experiment-forms/form-submit-b/process-plan/submission-check", route => fulfillJson(route, { ready: true, errors: [], warnings: [] }));
  await page.route("**/experiment-forms/form-submit-b/process-plan/revisions/source-revision", route => fulfillJson(route, revision("form-submit-b", sourcePlan)));
  await page.goto(harness("submit-revision"));
  await page.getByTestId("open-submit").click();
  const reason = page.getByPlaceholder("说明本次修改的原因");
  await expect(reason).toBeVisible();
  await reason.fill("表单 A 的原因");
  await page.getByText("我已核对版本差异").click();
  await page.getByText("我已核对配方、工艺得率和关键控制点").click();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeEnabled();
  await page.getByTestId("change-form").click({ force: true });
  await expect(reason).toHaveValue("");
  await expect(page.getByRole("button", { name: "确认提交" })).toBeDisabled();
});

test("flow destructive toggle is transactional on modal cancel and commit", async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  await page.goto(harness("flow"));
  await page.waitForTimeout(200);
  expect(pageErrors).toEqual([]);
  const toggle = page.getByText("继续流转", { exact: true });
  await toggle.click();
  await expect(page.getByText(/后段\/使用\/中间料/)).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("1");
  await expect(page.getByRole("checkbox", { name: "继续流转" })).toBeChecked();
  await toggle.click();
  await page.getByRole("button", { name: /继续并修复/ }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("0");
});

test("artifact list and generation interleavings converge on the authoritative list", async ({ page }) => {
  const lists: Route[] = [];
  let generateRoute: Route | undefined;
  await page.route("**/experiment-forms/form-artifacts/process-plan/revisions/revision-1/artifacts", async route => {
    if (route.request().method() === "GET") lists.push(route);
    else generateRoute = route;
  });
  await page.goto(harness("artifacts"));
  await expect.poll(() => lists.length).toBe(1);
  await page.getByRole("button", { name: /生\s*成/ }).first().click();
  await expect.poll(() => Boolean(generateRoute)).toBe(true);
  await expect(page.getByRole("button", { name: /生\s*成/ }).nth(1)).toBeDisabled();
  await fulfillJson(generateRoute!, artifact("formula", "FORMULA_XLSX"));
  await expect.poll(() => lists.length).toBe(2);
  await fulfillJson(lists[0]!, [artifact("sop", "SOP_DOCX")]);
  await fulfillJson(lists[1]!, [artifact("formula", "FORMULA_XLSX"), artifact("sop", "SOP_DOCX")]);
  await expect(page.getByText("FORMULA-R1")).toBeVisible();
  await expect(page.getByText("SOP-R1")).toBeVisible();

  generateRoute = undefined;
  await page.getByRole("button", { name: /重新生成/ }).nth(1).click();
  await expect.poll(() => Boolean(generateRoute)).toBe(true);
  await fulfillJson(generateRoute!, null, 500);
  await expect.poll(() => lists.length).toBe(3);
  await fulfillJson(lists[2]!, [artifact("formula", "FORMULA_XLSX"), artifact("sop", "SOP_DOCX")]);
  await expect(page.getByText("SOP-R1")).toBeVisible();
});

function artifact(id: string, artifactType: "FORMULA_XLSX" | "SOP_DOCX") {
  return { id, processRevisionId: "revision-1", artifactType, documentVersion: artifactType === "FORMULA_XLSX" ? "FORMULA-R1" : "SOP-R1", status: "READY", generatedAt: "2026-08-20T00:00:00Z" };
}

function revision(formId: string, snapshot: unknown) {
  return { id: "source-revision", processPlanId: "source-plan", experimentFormId: formId, revisionNo: 1, submittedAt: "2026-08-19T00:00:00Z", snapshotHash: "source", snapshot };
}

type ProcessPlanPayload = { versionNo: number; majorProcesses: Array<{ processName: string }> };
