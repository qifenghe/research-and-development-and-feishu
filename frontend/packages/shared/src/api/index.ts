import type { ApiClient } from "./client";
import type {
  MobileTodoBoard,
  MobileTodoGroup,
  MobileTodoItem,
  PricingFileRecord,
  RndTask,
  RndTaskStatus,
  ShipmentRecord,
} from "../types";
import { RND_TASK_STATUS_LABELS } from "../types";
import { createSessionApi, createDashboardApi } from "./session";
import { createSampleApi } from "./sample";
import { createTaskApi } from "./task";
import { createShipmentApi } from "./shipment";
import { createSettingsApi } from "./settings";

const TASK_GROUPS: Array<{ key: string; title: string; statuses: RndTaskStatus[] }> = [
  { key: "accept", title: "待接受任务", statuses: ["PENDING_ACCEPTANCE"] },
  { key: "sampling", title: "打样中实验单", statuses: ["SAMPLING"] },
  { key: "test", title: "待内部测试", statuses: ["PENDING_TEST"] },
];

const HERO_PRIORITY = ["sampling", "accept", "test", "shipment-feedback", "pricing"];

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

export async function fetchMobileTodoBoard(deps: {
  listTasks: (params: { status?: string }) => Promise<RndTask[]>;
  listShipments: (params: { status?: string }) => Promise<ShipmentRecord[]>;
  listPricingFiles: (params: { status?: string }) => Promise<PricingFileRecord[]>;
}): Promise<MobileTodoBoard> {
  const [taskGroupsRaw, shipped, feedbackPassed, pricingGenerated] = await Promise.all([
    Promise.all(
      TASK_GROUPS.map(async ({ key, title, statuses }) => {
        const batches = await Promise.all(statuses.map((status) => deps.listTasks({ status })));
        const items = batches.flat().map(taskToItem);
        return { key, title, items } satisfies MobileTodoGroup;
      }),
    ),
    deps.listShipments({ status: "SHIPPED" }),
    deps.listShipments({ status: "FEEDBACK_PASSED" }),
    deps.listPricingFiles({ status: "GENERATED" }),
  ]);

  const shipmentFeedbackGroup: MobileTodoGroup = {
    key: "shipment-feedback",
    title: "待寄样反馈",
    items: shipped.map((s) => shipmentToItem(s, "登记反馈")),
  };

  const pricingPending = feedbackPassed.filter(
    (shipment) => !pricingGenerated.some((file) => file.versionId === shipment.versionId),
  );
  const pricingGroup: MobileTodoGroup = {
    key: "pricing",
    title: "待生成核价",
    items: pricingPending.map((s) => shipmentToItem(s, "生成核价")),
  };

  const groups = [...taskGroupsRaw, shipmentFeedbackGroup, pricingGroup].filter(
    (group) => group.items.length > 0,
  );

  const totalCount = groups.reduce((sum, group) => sum + group.items.length, 0);
  const overdueCount = taskGroupsRaw
    .flatMap((group) => group.items)
    .filter((item) => item.dueDate && item.dueDate < todayString())
    .length;

  let hero: MobileTodoItem | null = null;
  for (const key of HERO_PRIORITY) {
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

export function createRndApi(client: ApiClient) {
  return {
    session: createSessionApi(client),
    dashboard: createDashboardApi(client),
    sample: createSampleApi(client),
    task: createTaskApi(client),
    shipment: createShipmentApi(client),
    settings: createSettingsApi(client),
  };
}

export type RndApi = ReturnType<typeof createRndApi>;
