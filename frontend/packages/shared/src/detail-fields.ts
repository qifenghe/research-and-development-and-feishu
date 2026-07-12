import type {
  CustomerFeedback,
  DetailField,
  DetailFieldGroup,
  ExperimentForm,
  FinanceNotification,
  PricingFileDetailView,
  PricingFileRecord,
  RndTask,
  RndTaskDetailView,
  RndTaskStatus,
  ShipmentRecord,
  SampleVersion,
  TestAssignment,
} from "./types";
import { RND_TASK_STATUS_LABELS } from "./types";

const FIELD_LABELS: Record<string, string> = {
  sampleNo: "样品编号",
  productName: "产品名称",
  productType: "产品类型",
  customerName: "客户名称",
  specification: "产品规格",
  versionCode: "样品版本",
  applicationScenario: "应用场景",
  flavorRequirement: "风味要求",
  status: "任务状态",
  assigneeName: "研发负责人",
  dueDate: "截止日期",
  createdAt: "创建时间",
  assignedAt: "分发时间",
  "currentExperimentForm.id": "实验单编号",
  "currentExperimentForm.status": "实验单状态",
  "currentExperimentForm.operatorName": "录入人",
  "currentExperimentForm.summary": "实验摘要",
  "currentExperimentForm.materials": "物料明细",
  "currentTestAssignment.id": "测试任务编号",
  "currentTestAssignment.testerName": "测试人员",
  "currentTestAssignment.status": "测试状态",
  "currentTestAssignment.assignedAt": "测试派发时间",
  "pricingFile.id": "核价文件编号",
  "pricingFile.sampleNo": "样品编号",
  "pricingFile.productName": "产品名称",
  "pricingFile.versionCode": "样品版本",
  "pricingFile.pricingVersion": "核价版本",
  "pricingFile.fileName": "文件名",
  "pricingFile.status": "状态",
  "pricingFile.generatedAt": "生成时间",
  "pricingFile.reviewedBy": "审核人",
  "pricingFile.reviewedAt": "审核时间",
  "pricingFile.reviewComment": "审核意见",
  "pricingFile.rejectionReason": "退回原因",
  "financeNotification.recipientName": "财务接收人",
  "financeNotification.remark": "通知备注",
  "financeNotification.status": "通知状态",
  "financeNotification.notifiedAt": "通知时间",
  "shipment.id": "寄样记录编号",
  "shipment.sampleNo": "样品编号",
  "shipment.productName": "产品名称",
  "shipment.versionCode": "样品版本",
  "shipment.quantity": "寄样数量",
  "shipment.receiverName": "收件人",
  "shipment.trackingNo": "快递单号",
  "shipment.remark": "备注",
  "shipment.status": "寄样状态",
  "shipment.shippedAt": "寄样时间",
  "customerFeedback.feedbackBy": "反馈人",
  "customerFeedback.result": "反馈结论",
  "customerFeedback.comment": "反馈说明",
  "customerFeedback.feedbackAt": "反馈时间",
};

const PRICING_FILE_STATUS_LABELS: Record<string, string> = {
  PENDING_PRICING_REVIEW: "待核价审核",
  PRICING_APPROVED: "核价审核通过",
  PRICING_REJECTED: "核价已退回",
  FINANCE_NOTIFIED: "已通知财务",
  FINANCE_RECEIVED: "财务已接收",
};

const SHIPMENT_STATUS_LABELS: Record<string, string> = {
  SHIPPED: "已寄出",
  FEEDBACK_PASSED: "客户已通过",
  FEEDBACK_FAILED_RESAMPLE: "客户要求复打样",
  STOPPED: "已停止打样",
};

const CUSTOMER_FEEDBACK_RESULT_LABELS: Record<string, string> = {
  PASSED: "客户通过",
  FAILED_RESAMPLE: "客户不通过，继续打样",
  STOPPED: "停止打样",
};

const EXPERIMENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: "草稿",
  SUBMITTED_FOR_TEST: "已提交测试",
  LOCKED: "已锁定",
};

function taskStatusLabel(status: RndTaskStatus): string {
  return RND_TASK_STATUS_LABELS[status] ?? status;
}

function formatValue(value: unknown): string {
  if (value == null || value === "") return "—";
  if (Array.isArray(value)) {
    if (value.length === 0) return "—";
    return value
      .map((item) => {
        if (typeof item === "object" && item && "materialName" in item) {
          const material = item as { materialName?: string; weightKg?: number; stage?: string };
          return `${material.stage ?? "原料"} / ${material.materialName ?? ""} / ${material.weightKg ?? 0}kg`;
        }
        return JSON.stringify(item);
      })
      .join("；");
  }
  if (typeof value === "string" && /\d{4}-\d{2}-\d{2}T/.test(value)) {
    return value.replace("T", " ").split(".")[0] ?? value;
  }
  return String(value);
}

function readPath(detail: RndTaskDetailView, path: string): unknown {
  const { task, version, project, currentExperimentForm, currentTestAssignment } = detail;
  if (path === "customerName") return project?.customerName;
  if (path === "productType") return project?.productType;
  if (path === "specification") return project?.specification ?? version.specification;
  if (path === "applicationScenario") return project?.applicationScenario ?? version.applicationScenario;
  if (path === "flavorRequirement") return project?.flavorRequirement ?? version.flavorRequirement;
  if (path === "sampleNo") return task.sampleNo || version.sampleNo;
  if (path === "productName") return task.productName || version.productName;
  if (path === "versionCode") return task.versionCode || version.versionCode;
  if (path === "status") {
    return taskStatusLabel(task.status);
  }
  if (path in task) {
    const key = path as keyof RndTask;
    if (key === "status") return taskStatusLabel(task.status);
    return task[key];
  }
  if (path in version) {
    return version[path as keyof typeof version];
  }
  if (path.startsWith("currentExperimentForm.")) {
    if (!currentExperimentForm) return null;
    const key = path.slice("currentExperimentForm.".length) as keyof ExperimentForm;
    if (key === "status") return EXPERIMENT_STATUS_LABELS[currentExperimentForm.status] ?? currentExperimentForm.status;
    if (key === "materials") return currentExperimentForm.materials;
    return currentExperimentForm[key];
  }
  if (path.startsWith("currentTestAssignment.")) {
    if (!currentTestAssignment) return null;
    const key = path.slice("currentTestAssignment.".length) as keyof TestAssignment;
    return currentTestAssignment[key];
  }
  return null;
}

export function resolveDetailFieldGroups(detail: RndTaskDetailView): Array<{ title: string; fields: DetailField[] }> {
  const { fieldGroups } = detail;
  return fieldGroups.map((group) => ({
    title: group.title,
    fields: group.fields.map((field) => {
      if (typeof field === "object" && field && "label" in field) {
        return field;
      }
      const path = String(field);
      return {
        label: FIELD_LABELS[path] ?? path,
        value: formatValue(readPath(detail, path)),
      };
    }),
  }));
}

export function buildTaskSummaryRows(detail: RndTaskDetailView): DetailField[] {
  const { task, version, project } = detail;
  const rows: DetailField[] = [
    { label: "样品编号", value: task.sampleNo || version.sampleNo || "—" },
    { label: "产品名称", value: task.productName || version.productName || "—" },
    { label: "样品版本", value: task.versionCode || version.versionCode || "—" },
    { label: "任务状态", value: taskStatusLabel(task.status) },
  ];
  if (project?.customerName) rows.push({ label: "客户名称", value: project.customerName });
  const specification = project?.specification ?? version.specification;
  if (specification) rows.push({ label: "产品规格", value: specification });
  const applicationScenario = project?.applicationScenario ?? version.applicationScenario;
  const flavorRequirement = project?.flavorRequirement ?? version.flavorRequirement;
  const requirementNotes = [applicationScenario, flavorRequirement].filter(Boolean).join("；");
  if (requirementNotes) rows.push({ label: "需求说明", value: requirementNotes });
  if (task.assigneeName) rows.push({ label: "研发负责人", value: task.assigneeName });
  if (task.dueDate) rows.push({ label: "截止日期", value: task.dueDate });
  return rows;
}

export function canEditExperiment(detail: RndTaskDetailView, operatorName: string, role: string): boolean {
  const { task } = detail;
  if (task.status !== "SAMPLING") return false;
  return ["RND_ENGINEER", "RND_DIRECTOR", "RND"].includes(role) && task.assigneeName === operatorName;
}

export function canNotifyInternalTest(detail: RndTaskDetailView, operatorName: string, role: string): boolean {
  const { task, currentExperimentForm } = detail;
  if (task.status !== "SAMPLING" || !currentExperimentForm || currentExperimentForm.status !== "DRAFT") {
    return false;
  }
  if (role === "RND_ASSISTANT") {
    return true;
  }
  return canEditExperiment(detail, operatorName, role);
}

export function canOperateTask(detail: RndTaskDetailView, operatorName: string, role: string): boolean {
  const { task } = detail;
  if (task.status === "PENDING_ASSIGNMENT") {
    return role === "RND_DIRECTOR";
  }
  if (task.status === "PENDING_ACCEPTANCE") {
    return ["RND_ENGINEER", "RND_DIRECTOR", "RND"].includes(role) && task.assigneeName === operatorName;
  }
  if (task.status === "SAMPLING") {
    return canEditExperiment(detail, operatorName, role);
  }
  if (task.status === "PENDING_TEST") {
    return ["TESTER", "QA_TESTER"].includes(role);
  }
  if (task.status === "COMPLETED") {
    return role === "RND_ASSISTANT";
  }
  return false;
}

export function resolveShipmentDetailFields(detail: {
  shipment: ShipmentRecord;
  version: SampleVersion;
  customerFeedback: CustomerFeedback | null;
  fieldGroups: DetailFieldGroup[];
}): Array<{ title: string; fields: DetailField[] }> {
  const { shipment, customerFeedback, fieldGroups } = detail;
  return fieldGroups.map((group) => ({
    title: group.title,
    fields: group.fields.map((field) => {
      if (typeof field === "object" && field && "label" in field) return field;
      const path = String(field);
      let value: unknown = null;
      if (path.startsWith("shipment.")) {
        const key = path.slice("shipment.".length) as keyof ShipmentRecord;
        value = shipment[key];
        if (key === "status" && typeof value === "string") {
          value = SHIPMENT_STATUS_LABELS[value] ?? value;
        }
      } else if (path.startsWith("customerFeedback.") && customerFeedback) {
        const key = path.slice("customerFeedback.".length) as keyof CustomerFeedback;
        value = customerFeedback[key];
        if (key === "result" && typeof value === "string") {
          value = CUSTOMER_FEEDBACK_RESULT_LABELS[value] ?? value;
        }
      }
      return { label: FIELD_LABELS[path] ?? path, value: formatValue(value) };
    }),
  }));
}

export function resolvePricingDetailFields(
  detail: PricingFileDetailView,
): Array<{ title: string; fields: DetailField[] }> {
  const { pricingFile, financeNotification, fieldGroups } = detail;
  return fieldGroups.map((group) => ({
    title: group.title,
    fields: group.fields.map((field) => {
      if (typeof field === "object" && field && "label" in field) return field;
      const path = String(field);
      let value: unknown = null;
      if (path.startsWith("pricingFile.")) {
        const key = path.slice("pricingFile.".length) as keyof PricingFileRecord;
        value = pricingFile[key];
        if (key === "status" && typeof value === "string") {
          value = PRICING_FILE_STATUS_LABELS[value] ?? value;
        }
      } else if (path.startsWith("financeNotification.") && financeNotification) {
        const key = path.slice("financeNotification.".length) as keyof FinanceNotification;
        value = financeNotification[key];
      }
      return { label: FIELD_LABELS[path] ?? path, value: formatValue(value) };
    }),
  }));
}

export function taskStatusTone(status: RndTaskStatus): "default" | "processing" | "success" | "warning" | "error" {
  switch (status) {
    case "PENDING_ASSIGNMENT":
      return "warning";
    case "PENDING_ACCEPTANCE":
      return "processing";
    case "SAMPLING":
      return "success";
    case "PENDING_TEST":
      return "processing";
    case "COMPLETED":
      return "default";
    default:
      return "default";
  }
}
