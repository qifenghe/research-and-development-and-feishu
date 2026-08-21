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
  await expect(page.getByTestId("parent-majors")).toHaveText("服务端大工序");
  await expect(page.getByRole("article").getByText("服务端大工序", { exact: true })).toBeVisible();
  await page.getByText("原辅料准备", { exact: true }).dblclick();
  await page.getByRole("button", { name: "保存草稿" }).click();
  await expect.poll(() => saves.length).toBe(1);
  expect(saves[0]?.majorProcesses.map(item => item.processName)).toEqual(["服务端大工序", "原辅料准备"]);

  await page.getByTestId("accept-cache").click();
  await expect(page.getByTestId("parent-majors")).toHaveText("缓存大工序");
  await expect(page.getByText("待保存", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "保存草稿" }).click();
  await expect.poll(() => saves.length).toBe(2);
  expect(saves[1]?.majorProcesses.map(item => item.processName)).toEqual(["缓存大工序"]);
});

test("real experiment parent autosaves a proxied plan and reloads cleanly from task A to B", async ({ page }) => {
  const pageErrors: string[] = [];
  const detailLoads: string[] = [];
  const planLoads: string[] = [];
  const draftSaves: Array<{ taskId: string; summary?: string }> = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  await page.route("**/rnd-tasks/*/detail?*", route => {
    const taskId = pathSegment(route.request().url(), /\/rnd-tasks\/([^/]+)\/detail/);
    detailLoads.push(taskId);
    return ["task-a", "task-b"].includes(taskId)
      ? fulfillJson(route, taskDetail(taskId))
      : fulfillJson(route, null, 404);
  });
  await page.route("**/experiment-forms/*/process-plan/revisions", route => fulfillJson(route, []));
  await page.route("**/experiment-forms/*/process-plan", route => {
    const formId = pathSegment(route.request().url(), /\/experiment-forms\/([^/]+)\/process-plan/);
    planLoads.push(formId);
    return ["form-a", "form-b"].includes(formId)
      ? fulfillJson(route, parentPlan(formId))
      : fulfillJson(route, null, 404);
  });
  await page.route("**/rnd-tasks/*/experiment-form/draft", async route => {
    const taskId = pathSegment(route.request().url(), /\/rnd-tasks\/([^/]+)\/experiment-form\/draft/);
    const payload = route.request().postDataJSON() as { summary?: string };
    draftSaves.push({ taskId, summary: payload.summary });
    if (["task-a", "task-b"].includes(taskId)) await fulfillJson(route, experimentForm(taskId, payload.summary || ""));
    else await fulfillJson(route, null, 404);
  });

  await page.goto(harness("experiment-parent"));
  await expect(page.getByText("产品 A V-A", { exact: true })).toBeVisible();
  await expect(page.getByText("服务端工序 A", { exact: true })).toBeVisible();
  expect(detailLoads).toEqual(["task-a"]);
  expect(planLoads).toEqual(["form-a"]);
  await page.getByPlaceholder("填写本次打样说明").fill("任务 A 本地修改");
  await expect.poll(() => draftSaves.filter(item => item.taskId === "task-a").length, { timeout: 5000 }).toBe(1);
  const cached = await page.evaluate(() => localStorage.getItem("rnd:experiment-draft:v1:engineer-1:task-a"));
  expect(cached).toContain("任务 A 本地修改");

  await page.getByTestId("route-task-b").click();
  await expect(page.getByTestId("active-task-route")).toHaveText("task-b");
  await expect(page.getByText("产品 B V-B", { exact: true })).toBeVisible();
  await expect(page.getByText("服务端工序 B", { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder("填写本次打样说明")).toHaveValue("服务端摘要 B");
  expect(detailLoads).toEqual(["task-a", "task-b"]);
  expect(planLoads).toEqual(["form-a", "form-b"]);
  expect(pageErrors).toEqual([]);
  await expect.poll(() => page.locator("body").getAttribute("data-harness-error")).toBeNull();
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
  await expect.poll(() => checks).toBe(1);
  await page.getByText("我已核对配方、工艺得率和关键控制点").click();
  await expect(page.getByRole("checkbox", { name: /我已核对配方、工艺得率和关键控制点/ })).toBeChecked();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeEnabled();
  await page.getByTestId("bump-version").evaluate((element: HTMLElement) => element.click());
  await expect.poll(() => checks).toBe(2);
  await expect(page.getByText("failed")).toBeVisible();
  await expect(page.getByRole("button", { name: "确认提交" })).toBeDisabled();
  await page.locator('[role="dialog"]:visible').getByRole("button", { name: /^(?:Cancel|取\s*消)$/ }).click();
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
  await page.getByTestId("change-form").evaluate((element: HTMLElement) => element.click());
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
  await page.locator('[role="dialog"]:visible').getByRole("button", { name: /^(?:Cancel|取\s*消)$/ }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("1");
  await expect(page.getByRole("checkbox", { name: "继续流转" })).toBeChecked();
  await toggle.click();
  await page.locator('[role="dialog"]:visible').getByRole("button", { name: /继续并修复/ }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("0");
});

test("ordinary flow edits also require transactional repair confirmation", async ({ page }) => {
  await page.goto(harness("flow"));
  await page.getByTestId("inject-broken-flow").click();
  await page.getByRole("button", { name: "编辑" }).click();
  const stepName = page.locator(".ant-form-item").filter({ hasText: "步骤名称" }).getByRole("textbox");
  await expect(stepName).toHaveValue("产出");
  await stepName.fill("产出已编辑");
  await expect(page.getByText(/后段\/使用\/中间料/)).toBeVisible();
  await page.locator('[role="dialog"]:visible').getByRole("button", { name: /^(?:Cancel|取\s*消)$/ }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("1");
  await expect(stepName).toHaveValue("产出");
  await expect(page.getByRole("button", { name: /继续并修复/ })).toHaveCount(0);

  await stepName.fill("产出已编辑");
  await page.locator('[role="dialog"]:visible').getByRole("button", { name: /继续并修复/ }).click();
  await expect(page.getByTestId("consumer-count")).toHaveText("0");
  await expect(stepName).toHaveValue("产出已编辑");
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

function pathSegment(url: string, pattern: RegExp) {
  return decodeURIComponent(pattern.exec(url)?.[1] || "");
}

function parentPlan(formId: string) {
  const suffix = formId === "form-b" ? "B" : "A";
  return {
    versionNo: 3,
    status: "DRAFT",
    balanceToleranceKg: 0.01,
    majorProcesses: [{
      id: `major-${suffix.toLowerCase()}`,
      key: `major-${suffix.toLowerCase()}`,
      sequence: 1,
      processName: `服务端工序 ${suffix}`,
      yieldBasis: "NONE",
      inputs: [], outputs: [], steps: [],
    }],
  };
}

function experimentForm(taskId: string, summary = taskId === "task-b" ? "服务端摘要 B" : "服务端摘要 A") {
  const suffix = taskId === "task-b" ? "b" : "a";
  return {
    id: `form-${suffix}`,
    taskId,
    projectId: `project-${suffix}`,
    versionId: `version-${suffix}`,
    sampleNo: `S-${suffix}`,
    productName: `产品 ${suffix.toUpperCase()}`,
    versionCode: `V-${suffix.toUpperCase()}`,
    status: "DRAFT",
    operatorName: "研发工程师",
    summary,
    materials: [],
    processSteps: [{ sequence: 1, processName: `旧版工序 ${suffix.toUpperCase()}` }],
    yieldCalculationMode: "SELECTED_PRIMARY_MATERIALS",
    savedAt: "2026-08-20T00:00:00Z",
    submittedAt: "",
  };
}

function taskDetail(taskId: string) {
  const suffix = taskId === "task-b" ? "b" : "a";
  return {
    task: {
      id: taskId,
      projectId: `project-${suffix}`,
      versionId: `version-${suffix}`,
      sampleNo: `S-${suffix}`,
      productName: `产品 ${suffix.toUpperCase()}`,
      versionCode: `V-${suffix.toUpperCase()}`,
      status: "SAMPLING",
      assigneeName: "研发工程师",
      productOwnerName: "产品经理",
      dueDate: "2026-08-30",
      createdAt: "2026-08-19T00:00:00Z",
      assignedAt: "2026-08-19T00:00:00Z",
    },
    version: {
      id: `version-${suffix}`,
      projectId: `project-${suffix}`,
      versionCode: `V-${suffix.toUpperCase()}`,
      versionNo: suffix === "b" ? 2 : 1,
      status: "SAMPLING",
      specification: `规格 ${suffix.toUpperCase()}`,
    },
    project: null,
    currentExperimentForm: experimentForm(taskId),
    currentTestAssignment: null,
    fieldGroups: [],
    availableActions: [],
  };
}
