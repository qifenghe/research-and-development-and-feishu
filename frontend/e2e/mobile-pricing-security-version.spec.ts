import { expect, test, type APIRequestContext } from "@playwright/test";

const API_BASE_URL = process.env.BACKEND_BASE_URL ?? "http://127.0.0.1:8080";

type Session = { token: string; name: string };
type LockedVersion = { projectId: string; versionId: string; testAssignmentId: string };

async function api(
  request: APIRequestContext,
  method: "GET" | "POST",
  path: string,
  options: { token?: string; data?: unknown } = {},
) {
  const response = await request.fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: options.token ? { Authorization: `Bearer ${options.token}` } : undefined,
    data: options.data,
  });
  return { response, body: await response.json() };
}

async function login(request: APIRequestContext, username: string, name: string): Promise<Session> {
  const result = await api(request, "POST", "/api/v1/auth/login", {
    data: { username, password: "123456" },
  });
  expect(result.response).toBeOK();
  return { token: result.body.data.accessToken, name };
}

async function createTestableVersion(
  request: APIRequestContext,
  suffix: string,
  sessions: { assistant: Session; director: Session; engineer: Session; tester: Session },
  lock = true,
): Promise<LockedVersion> {
  const created = await api(request, "POST", "/api/v1/sample-requests", {
    token: sessions.assistant.token,
    data: {
      productName: `H5核价安全回归${suffix}`,
      productType: "冷冻即热菜",
      customerName: "闭环验收客户",
      specification: "500g/袋",
      applicationScenario: "餐饮渠道复热出餐",
      flavorRequirement: "香辣，复热后风味稳定",
      creatorName: sessions.assistant.name,
    },
  });
  expect(created.response).toBeOK();

  const approved = await api(request, "POST", `/api/v1/sample-requests/${created.body.data.id}/approve`, {
    token: sessions.director.token,
    data: { reviewerName: sessions.director.name },
  });
  expect(approved.response).toBeOK();
  const taskId = approved.body.data.task.id as string;

  expect((await api(request, "POST", `/api/v1/rnd-tasks/${taskId}/assign`, {
    token: sessions.director.token,
    data: { assigneeName: sessions.engineer.name, dueDate: "2026-07-31" },
  })).response).toBeOK();
  expect((await api(request, "POST", `/api/v1/rnd-tasks/${taskId}/accept`, {
    token: sessions.engineer.token,
    data: { acceptedBy: sessions.engineer.name },
  })).response).toBeOK();

  const draft = await api(request, "POST", `/api/v1/rnd-tasks/${taskId}/experiment-form/draft`, {
    token: sessions.engineer.token,
    data: {
      operatorName: sessions.engineer.name,
      summary: "真实 H5 API 回归实验数据",
      materials: [
        { stage: "原料", sequence: 1, materialCode: "RAW-001", materialName: "主料", weightKg: 10, primaryMaterial: true },
        { stage: "辅料", sequence: 2, materialCode: "AUX-001", materialName: "辅料", weightKg: 2 },
      ],
      processSteps: [{
        sequence: 1,
        processName: "卤制",
        beforeWeightKg: 12,
        afterWeightKg: 10,
        remainingWeightKg: 0.5,
        remainingDisposition: "REUSE",
      }],
      finishedOutputWeightKg: 10,
      finishedOutputQuantity: 20,
      finishedOutputUnit: "袋",
    },
  });
  expect(draft.response).toBeOK();

  const submitted = await api(request, "POST", `/api/v1/experiment-forms/${draft.body.data.id}/submit-test`, {
    token: sessions.engineer.token,
    data: { testerName: sessions.tester.name },
  });
  expect(submitted.response).toBeOK();
  const testAssignmentId = submitted.body.data.testAssignment.id as string;
  if (!lock) {
    return { projectId: approved.body.data.project.id, versionId: draft.body.data.versionId, testAssignmentId };
  }

  const passed = await api(request, "POST", `/api/v1/test-assignments/${testAssignmentId}/pass`, {
    token: sessions.tester.token,
    data: { testerName: sessions.tester.name, comment: "真实会话测试通过" },
  });
  expect(passed.response).toBeOK();
  return { projectId: approved.body.data.project.id, versionId: passed.body.data.experimentForm.versionId, testAssignmentId };
}

test("H5 sessions enforce pricing review gates and preserve pricing versions", async ({ request }) => {
  test.setTimeout(120_000);
  const suffix = `${Date.now()}`.slice(-8);
  const sessions = {
    assistant: await login(request, "rnd_assistant", "赵内勤"),
    director: await login(request, "rnd_director", "赵总监"),
    engineer: await login(request, "rnd_engineer", "张研发"),
    tester: await login(request, "tester", "李测试"),
    finance: await login(request, "finance", "钱财务"),
  };
  const locked = await createTestableVersion(request, `${suffix}V2`, sessions);

  const first = await api(request, "POST", `/api/v1/sample-versions/${locked.versionId}/pricing-files`, {
    token: sessions.assistant.token,
    data: {},
  });
  expect(first.response).toBeOK();
  expect(first.body.data.pricingVersion).toBe("A0-核价V1");

  const prematureNotify = await api(request, "POST", `/api/v1/pricing-files/${first.body.data.id}/notify-finance`, {
    token: sessions.assistant.token,
    data: { recipientName: sessions.finance.name, remark: "未审核通知" },
  });
  expect(prematureNotify.response.status()).toBe(400);
  expect(prematureNotify.body.code).toBe("PRICING_FILE_REVIEW_REQUIRED");

  const unauthorizedReview = await api(request, "POST", `/api/v1/pricing-files/${first.body.data.id}/review`, {
    token: sessions.finance.token,
    data: { decision: "APPROVE", comment: "越权审核" },
  });
  expect(unauthorizedReview.response.status()).toBe(403);
  expect(unauthorizedReview.body.code).toBe("SESSION_ROLE_FORBIDDEN");

  const rejected = await api(request, "POST", `/api/v1/pricing-files/${first.body.data.id}/review`, {
    token: sessions.engineer.token,
    data: { decision: "REJECT", comment: "原料规格需要修正" },
  });
  expect(rejected.response).toBeOK();
  expect(rejected.body.data.status).toBe("PRICING_REJECTED");

  const second = await api(request, "POST", `/api/v1/sample-versions/${locked.versionId}/pricing-files`, {
    token: sessions.assistant.token,
    data: {},
  });
  expect(second.response).toBeOK();
  expect(second.body.data.pricingVersion).toBe("A0-核价V2");

  const allPricing = await api(request, "GET", "/api/v1/pricing-files", { token: sessions.assistant.token });
  expect(allPricing.response).toBeOK();
  expect(allPricing.body.data).toEqual(expect.arrayContaining([
    expect.objectContaining({ id: first.body.data.id, pricingVersion: "A0-核价V1", status: "PRICING_REJECTED" }),
    expect.objectContaining({ id: second.body.data.id, pricingVersion: "A0-核价V2" }),
  ]));
});

test("H5 sessions create A1 after an experiment-data failure and preserve locked A0", async ({ request }) => {
  test.setTimeout(120_000);
  const suffix = `${Date.now()}`.slice(-8);
  const sessions = {
    assistant: await login(request, "rnd_assistant", "赵内勤"),
    director: await login(request, "rnd_director", "赵总监"),
    engineer: await login(request, "rnd_engineer", "张研发"),
    tester: await login(request, "tester", "李测试"),
  };
  const locked = await createTestableVersion(request, `${suffix}A1`, sessions, false);

  const failed = await api(request, "POST", `/api/v1/test-assignments/${locked.testAssignmentId}/fail-resample`, {
    token: sessions.tester.token,
    data: { testerName: sessions.tester.name, comment: "实验数据异常，需要复打样" },
  });
  expect(failed.response).toBeOK();
  expect(failed.body.data.nextVersion.versionCode).toBe("A1");

  const timeline = await api(request, "GET", `/api/v1/sample-projects/${locked.projectId}/version-timeline`, {
    token: sessions.director.token,
  });
  expect(timeline.response).toBeOK();
  expect(timeline.body.data).toEqual(expect.arrayContaining([
    expect.objectContaining({ versionCode: "A0", statusLabel: "已锁定", locked: true, current: false }),
    expect.objectContaining({ versionCode: "A1" }),
  ]));
});
