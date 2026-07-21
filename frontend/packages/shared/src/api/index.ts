import type { ApiClient } from "./client";
import type {
  MobileTodoBoard,
  MobileTodoGroup,
  MobileTodoItem,
  PricingFileRecord,
  PricingReadyVersion,
  RndTask,
  RndTaskStatus,
  ShipmentRecord,
} from "../types";
import { RND_TASK_STATUS_LABELS } from "../types";
import { canAccessRoute } from "../permissions";
import { createSessionApi, createDashboardApi } from "./session";
import { createSampleApi } from "./sample";
import { createTaskApi } from "./task";
import { createShipmentApi } from "./shipment";
import { createSettingsApi } from "./settings";
import { createReportApi } from "./report";
import { pricingInboxStatusForRole, pricingTodoGroupTitle } from "./mobile-todo-policy";

const TASK_GROUPS: Array<{ key: string; title: string; statuses: RndTaskStatus[] }> = [
  { key: "accept", title: "待接受任务", statuses: ["PENDING_ACCEPTANCE"] },
  { key: "sampling", title: "打样中实验单", statuses: ["SAMPLING"] },
  { key: "test", title: "待内部测试", statuses: ["PENDING_TEST"] },
];

const HERO_PRIORITY = ["sampling", "accept", "test", "shipment-feedback", "pricing"];
const ASSISTANT_HERO_PRIORITY = ["shipment-feedback", "pricing", "sampling"];

function heroPriorityForRole(role: string): string[] {
  return role === "RND_ASSISTANT" ? ASSISTANT_HERO_PRIORITY : HERO_PRIORITY;
}

function taskGroupsForRole(role: string): Array<{ key: string; title: string; statuses: RndTaskStatus[] }> {
  if (role === "RND_ASSISTANT") {
    return TASK_GROUPS.filter((group) => group.key === "sampling");
  }
  if (role === "TESTER" || role === "QA_TESTER") {
    return TASK_GROUPS.filter((group) => group.key === "test");
  }
  if (role === "RND_ENGINEER" || role === "RND_DIRECTOR" || role === "RND") {
    return TASK_GROUPS.filter((group) => group.key !== "test");
  }
  if (role === "FINANCE") {
    return [];
  }
  return TASK_GROUPS;
}

function taskToItem(task: RndTask): MobileTodoItem {
  return {
    id: task.id,
    kind: "task",
    productName: task.productName,
    versionCode: task.versionCode,
    statusLabel: taskStatusLabel(task.status),
    actionLabel: taskActionLabel(task),
    route: taskPrimaryRoute(task),
    subtitle: taskStatusLabel(task.status),
    dueDate: task.dueDate,
  };
}

function shipmentToItem(shipment: ShipmentRecord, actionLabel: string): MobileTodoItem {
  return {
    id: shipment.id,
    kind: "shipment",
    productName: shipment.productName,
    versionCode: shipment.versionCode,
    statusLabel: shipment.status === "SHIPPED" ? "待反馈" : "待核价",
    actionLabel,
    route: `/shipments/${shipment.id}`,
    subtitle: shipment.status === "SHIPPED" ? "已寄样" : "客户已通过",
  };
}

function pricingToItem(pricing: PricingFileRecord): MobileTodoItem {
  return {
    id: pricing.id,
    kind: "pricing",
    productName: pricing.productName,
    versionCode: pricing.versionCode,
    statusLabel: pricing.status === "FINANCE_NOTIFIED" ? "已通知财务" : "待财务核价",
    actionLabel: "查看/下载",
    route: `/pricing/${pricing.id}`,
    subtitle: pricing.pricingVersion,
  };
}

function pricingReadyToItem(version: PricingReadyVersion): MobileTodoItem {
  return {
    id: version.versionId,
    kind: "pricing",
    productName: version.productName,
    versionCode: version.versionCode,
    statusLabel: "测试已通过",
    actionLabel: "生成核价",
    route: `/pricing/create/${version.versionId}`,
    subtitle: version.sampleNo,
  };
}

export async function fetchMobileTodoBoard(deps: {
  role?: string | null;
  listTasks: (params: { status?: string }) => Promise<RndTask[]>;
  listShipments: (params: { status?: string }) => Promise<ShipmentRecord[]>;
  listPricingFiles: (params: { status?: string }) => Promise<PricingFileRecord[]>;
  listPricingReadyVersions?: () => Promise<PricingReadyVersion[]>;
}): Promise<MobileTodoBoard> {
  const role = deps.role ?? "";
  const canLoadShipments = role === "RND_ASSISTANT"
    || canAccessRoute(role, "/shipments/SHIP-0001", "mobile");
  const canLoadPricing = canAccessRoute(role, "/pricing/PRICE-0001", "mobile");

  const visibleTaskGroups = taskGroupsForRole(role);
  const [taskGroupsRaw, shipped, feedbackPassed, pricingInbox, pricingReady] = await Promise.all([
    Promise.all(
      visibleTaskGroups.map(async ({ key, title, statuses }) => {
        const batches = await Promise.all(statuses.map((status) => deps.listTasks({ status })));
        const items = batches.flat().map(taskToItem);
        return { key, title, items } satisfies MobileTodoGroup;
      }),
    ),
    canLoadShipments ? deps.listShipments({ status: "SHIPPED" }) : Promise.resolve([]),
    canLoadShipments ? deps.listShipments({ status: "FEEDBACK_PASSED" }) : Promise.resolve([]),
    canLoadPricing
      ? deps.listPricingFiles({ status: pricingInboxStatusForRole(role) })
      : Promise.resolve([]),
    role === "RND_ASSISTANT" && deps.listPricingReadyVersions
      ? deps.listPricingReadyVersions()
      : Promise.resolve([]),
  ]);

  const shipmentFeedbackGroup: MobileTodoGroup = {
    key: "shipment-feedback",
    title: "待寄样反馈",
    items: shipped.map((s) => shipmentToItem(s, "登记反馈")),
  };

  const legacyPricingReady = feedbackPassed
    .filter((shipment) => !pricingInbox.some((file) => file.versionId === shipment.versionId))
    .map((shipment) => ({
      versionId: shipment.versionId,
      sampleNo: shipment.sampleNo,
      productName: shipment.productName,
      versionCode: shipment.versionCode,
    }));
  const resolvedPricingReady = pricingReady.length > 0 ? pricingReady : legacyPricingReady;
  const pricingGroup: MobileTodoGroup = {
    key: "pricing",
    title: pricingTodoGroupTitle(role),
    items: role === "FINANCE"
      ? pricingInbox.map(pricingToItem)
      : resolvedPricingReady.map(pricingReadyToItem),
  };
  const notifyFinanceGroup: MobileTodoGroup = {
    key: "pricing-notify",
    title: "待通知财务",
    items: role === "RND_ASSISTANT" ? pricingInbox.map(pricingToItem) : [],
  };

  const groups = [...taskGroupsRaw, shipmentFeedbackGroup, pricingGroup, notifyFinanceGroup]
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => canAccessRoute(role, item.route, "mobile")),
    }))
    .filter((group) => group.items.length > 0);

  const totalCount = groups.reduce((sum, group) => sum + group.items.length, 0);
  const overdueCount = taskGroupsRaw
    .flatMap((group) => group.items)
    .filter((item) => item.dueDate && item.dueDate < todayString())
    .length;

  let hero: MobileTodoItem | null = null;
  for (const key of heroPriorityForRole(role)) {
    const group = groups.find((entry) => entry.key === key);
    if (group?.items[0]) {
      hero = group.items[0];
      break;
    }
  }

  return { groups, hero, totalCount, overdueCount };
}

/** @deprecated use fetchMobileTodoBoard */
export async function fetchMobileTodos(
  listTasks: (params: { status?: string }) => Promise<RndTask[]>,
): Promise<Array<{ key: string; title: string; tasks: RndTask[] }>> {
  const board = await fetchMobileTodoBoard({
    listTasks,
    listShipments: async () => [],
    listPricingFiles: async () => [],
  });
  return board.groups
    .filter((group) => group.key === "accept" || group.key === "sampling" || group.key === "test")
    .map((group) => ({
      key: group.key,
      title: group.title,
      tasks: group.items.map((item) => ({
        id: item.id,
        productName: item.productName,
        versionCode: item.versionCode,
        status: item.subtitle as RndTaskStatus,
      })) as RndTask[],
    }));
}

function todayString(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export function taskStatusLabel(status: RndTaskStatus): string {
  return RND_TASK_STATUS_LABELS[status] ?? status;
}

function taskActionLabel(task: RndTask): string {
  switch (task.status) {
    case "PENDING_ACCEPTANCE":
      return "接受";
    case "SAMPLING":
      return "继续填写";
    case "PENDING_TEST":
      return "测试确认";
    default:
      return "继续处理";
  }
}

export function taskPrimaryRoute(task: RndTask): string {
  switch (task.status) {
    case "PENDING_ACCEPTANCE":
      return `/tasks/${task.id}`;
    case "SAMPLING":
      return `/experiments/${task.id}`;
    case "PENDING_TEST":
      return `/tests/${task.id}`;
    default:
      return `/tasks/${task.id}`;
  }
}

export { createApiClient, ApiError } from "./client";
export { createSessionApi, createDashboardApi } from "./session";
export { createSampleApi } from "./sample";
export { createTaskApi } from "./task";
export { createShipmentApi } from "./shipment";
export { createSettingsApi } from "./settings";
export { createReportApi } from "./report";

export function createRndApi(client: ApiClient) {
  return {
    session: createSessionApi(client),
    dashboard: createDashboardApi(client),
    sample: createSampleApi(client),
    task: createTaskApi(client),
    shipment: createShipmentApi(client),
    settings: createSettingsApi(client),
    report: createReportApi(client),
  };
}

export type RndApi = ReturnType<typeof createRndApi>;
