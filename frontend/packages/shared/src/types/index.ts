export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface PagedResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export type SampleStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "REJECTED";

export type RndTaskStatus =
  | "PENDING_ASSIGNMENT"
  | "PENDING_ACCEPTANCE"
  | "SAMPLING"
  | "PENDING_TEST"
  | "COMPLETED";

export type ExperimentFormStatus =
  | "DRAFT"
  | "SUBMITTED_FOR_TEST"
  | "LOCKED";

export type MaterialCategory = "RAW" | "AUXILIARY" | "PACKAGING";
export type YieldCalculationMode = "SELECTED_PRIMARY_MATERIALS" | "TOTAL_PICKING_WEIGHT";

export type RemainingDisposition = "REUSE" | "RETURN" | "DISCARD";

export type PricingFileStatus =
  | "PENDING_PRICING_REVIEW"
  | "PRICING_APPROVED"
  | "PRICING_REJECTED"
  | "FINANCE_NOTIFIED"
  | "FINANCE_RECEIVED";

export interface UserAccount {
  id: string;
  username?: string;
  name: string;
  feishuUserId?: string;
  role: string;
  departmentName: string;
  status: string;
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SessionPrincipal {
  userId: string;
  username?: string;
  name: string;
  feishuUserId?: string;
  role: string;
  expiresAt: string;
}

export interface AuthLoginResult {
  user: UserAccount;
  accessToken: string;
}

export interface FeishuLoginResult extends AuthLoginResult {
  feishuUserId: string;
}

export interface SampleRequest {
  id: string;
  sampleNo: string;
  productName: string;
  productType: string;
  customerName: string;
  specification: string;
  applicationScenario: string;
  flavorRequirement: string;
  creatorName: string;
  status: SampleStatus;
  createdAt: string;
}

export interface DashboardTaskItem {
  taskId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  status: string;
  assigneeName: string;
  dueDate: string;
  createdAt: string;
}

export interface DashboardPricingFileItem {
  pricingFileId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  status: string;
}

export interface DashboardOverview {
  pendingReviewCount: number;
  pendingAssignmentCount: number;
  pendingAcceptanceCount: number;
  samplingCount: number;
  pendingTestCount: number;
  completedSampleCount: number;
  pendingPricingCount: number;
  financeNotifiedCount: number;
  stoppedCount: number;
  recentTasks: DashboardTaskItem[];
  pendingPricingFiles: DashboardPricingFileItem[];
}

export interface RndTask {
  id: string;
  projectId: string;
  versionId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  status: RndTaskStatus;
  assigneeName: string;
  productOwnerName: string;
  dueDate: string;
  createdAt: string;
  assignedAt: string;
}

export interface SampleVersion {
  id: string;
  projectId: string;
  versionCode: string;
  versionNo: number;
  status: string;
  sampleNo?: string;
  productName?: string;
  specification?: string;
  applicationScenario?: string;
  flavorRequirement?: string;
}

export interface ExperimentMaterial {
  id?: string;
  stage: string;
  sequence: number;
  materialCode?: string;
  materialName: string;
  weightKg: number;
  utilizationRate?: number;
  remark?: string;
  materialCategory?: MaterialCategory;
  primaryMaterial?: boolean;
  formulaRatio?: number;
  inputUnit?: string;
}

export interface ExperimentProcessStep {
  sequence: number;
  processName: string;
  beforeWeightKg?: number;
  afterWeightKg?: number;
  remainingWeightKg?: number;
  remainingDisposition?: RemainingDisposition;
  lossWeightKg?: number;
  lossRate?: number;
  remark?: string;
}

export interface ExperimentForm {
  id: string;
  taskId: string;
  projectId: string;
  versionId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  status: ExperimentFormStatus;
  operatorName: string;
  summary: string;
  materials: ExperimentMaterial[];
  processSteps?: ExperimentProcessStep[];
  finishedOutputWeightKg?: number;
  finishedOutputQuantity?: number;
  finishedOutputUnit?: string;
  yieldCalculationMode?: YieldCalculationMode;
  finishedYieldPercent?: number;
  savedAt: string;
  submittedAt: string;
}

export interface SampleVersionTimelineItem {
  versionId: string;
  versionCode: string;
  statusLabel: string;
  subtitle: string;
  summary?: string;
  locked: boolean;
  current: boolean;
}

export interface TemplateConfigItem {
  id: string;
  templateCode: string;
  templateName: string;
  templateType: string;
  filePath?: string;
  versionNo: string;
  status: string;
  updatedAt: string;
}

export interface TestAssignment {
  id: string;
  versionId: string;
  testerName: string;
  status: string;
}

export interface DetailField {
  label: string;
  value: string;
}

export interface DetailFieldGroup {
  title: string;
  fields: Array<string | DetailField>;
}

export interface DetailAction {
  code: string;
  label: string;
  httpMethod: string;
  endpoint: string;
  primary: boolean;
}

export interface SampleProjectSummary {
  id: string;
  sampleNo: string;
  productName: string;
  productType: string;
  customerName: string;
  specification: string;
  applicationScenario: string;
  flavorRequirement: string;
  status: string;
}

export interface RndTaskDetailView {
  task: RndTask;
  version: SampleVersion;
  project: SampleProjectSummary | null;
  currentExperimentForm: ExperimentForm | null;
  currentTestAssignment: TestAssignment | null;
  fieldGroups: DetailFieldGroup[];
  availableActions: DetailAction[];
}

export interface StoppedSampleProjectView {
  projectId: string;
  sampleNo: string;
  productName: string;
  productType: string;
  customerName: string;
  specification: string;
  status: string;
  lastVersionCode: string;
  stoppedBy: string;
  stopReason: string;
  stoppedAt: string;
}

export interface ShipmentRecord {
  id: string;
  versionId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  status: string;
  shippedAt: string;
  quantity?: number;
  receiverName?: string;
  trackingNo?: string;
  remark?: string;
}

export interface CustomerFeedback {
  id: string;
  shipmentId: string;
  result: string;
  comment: string;
  feedbackAt: string;
}

export interface ShipmentDetailView {
  shipment: ShipmentRecord;
  version: SampleVersion;
  customerFeedback: CustomerFeedback | null;
  fieldGroups: DetailFieldGroup[];
  availableActions: DetailAction[];
}

export interface PricingFileRecord {
  id: string;
  versionId: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
  pricingVersion: string;
  fileName: string;
  status: PricingFileStatus;
  generatedAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewComment?: string;
  rejectionReason?: string;
}

export interface PricingReadyVersion {
  versionId: string;
  taskId?: string;
  sampleNo: string;
  productName: string;
  versionCode: string;
}

export interface FinanceNotification {
  id: string;
  pricingFileId: string;
  recipientName: string;
  remark?: string;
  status: string;
  notifiedAt: string;
}

export interface PricingFileDetailView {
  pricingFile: PricingFileRecord;
  version: SampleVersion;
  financeNotification: FinanceNotification | null;
  fieldGroups: DetailFieldGroup[];
  availableActions: DetailAction[];
}

export interface ArchiveFileView {
  id: string;
  versionId: string;
  fileName: string;
  category: string;
  uploadedBy: string;
  uploadedAt: string;
}

export interface WorkflowConfig {
  workflowCode: string;
  workflowName: string;
  rules: WorkflowRule[];
}

export interface WorkflowRule {
  fromStatus: string;
  actionCode: string;
  actionLabel: string;
  toStatus: string;
  requireApproval: boolean;
  notifyRequired: boolean;
}

export interface DictionaryConfig {
  category: string;
  categoryName: string;
  items: DictionaryItem[];
}

export interface DictionaryItem {
  code: string;
  label: string;
  sortOrder: number;
  enabled: boolean;
}

export interface FormFieldConfig {
  formCode: string;
  formName: string;
  fields: FormFieldConfigItem[];
}

export interface FormFieldConfigItem {
  fieldCode: string;
  fieldLabel: string;
  required: boolean;
  visible: boolean;
  sortOrder: number;
}

export interface RolePermissionConfig {
  roleCode: string;
  roleName: string;
  rules: RolePermissionRule[];
}

export interface RolePermissionRule {
  httpMethod: string;
  pathPattern: string;
  allowed: boolean;
}

export interface MobileTodoItem {
  id: string;
  kind: "task" | "shipment" | "pricing";
  productName: string;
  versionCode: string;
  statusLabel: string;
  actionLabel: string;
  route: string;
  subtitle?: string;
  dueDate?: string;
}

export interface MobileTodoGroup {
  key: string;
  title: string;
  items: MobileTodoItem[];
}

export interface MobileTodoBoard {
  groups: MobileTodoGroup[];
  hero: MobileTodoItem | null;
  totalCount: number;
  overdueCount: number;
}

export const RND_TASK_STATUS_LABELS: Record<RndTaskStatus, string> = {
  PENDING_ASSIGNMENT: "待分发",
  PENDING_ACCEPTANCE: "待接受",
  SAMPLING: "打样中",
  PENDING_TEST: "待内部测试",
  COMPLETED: "已完成",
};

export const SAMPLE_STATUS_LABELS: Record<SampleStatus, string> = {
  DRAFT: "草稿",
  PENDING_REVIEW: "待审核",
  APPROVED: "已通过",
  REJECTED: "已退回",
};
