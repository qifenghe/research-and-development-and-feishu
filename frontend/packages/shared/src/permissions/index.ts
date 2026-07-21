const ADMIN_ROLES = ["ADMIN", "SYSTEM_ADMIN"] as const;

export type AppAction =
  | "APPROVE_REQUEST"
  | "ASSIGN_TASK"
  | "ACCEPT_TASK"
  | "SAVE_EXPERIMENT"
  | "NOTIFY_INTERNAL_TEST"
  | "PERFORM_INTERNAL_TEST"
  | "RECORD_SHIPMENT_FEEDBACK"
  | "MANAGE_SETTINGS";

type RouteRule = {
  pattern: RegExp;
  roles: string[];
};

/** Page-level access aligned with RolePermissionService defaults and RND-002. */
const ROUTE_RULES: RouteRule[] = [
  { pattern: /^\/dashboard$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/flowchart$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/demand$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/demand\/new$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/demand\/list$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/demand\/review$/, roles: ["RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/demand\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/pool$/, roles: ["RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/assign$/, roles: ["RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/my-tasks$/, roles: ["RND_ENGINEER", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/pending-tests$/, roles: ["TESTER", "QA_TESTER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/stopped$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/tasks\/[^/]+$/, roles: ["RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/tasks\/[^/]+\/experiment$/, roles: ["RND_ENGINEER", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/tasks\/[^/]+\/test$/, roles: ["TESTER", "QA_TESTER", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/tasks\/[^/]+\/feedback$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/rnd\/history\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/shipment$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/shipment\/list$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/shipment\/record$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/shipment\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/pricing\/list$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/pricing\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/finance$/, roles: ["RND_ASSISTANT", "FINANCE", ...ADMIN_ROLES] },
  { pattern: /^\/archive$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/reports$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/settings/, roles: [...ADMIN_ROLES] },
  { pattern: /^\/403$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
];

const MOBILE_ROUTE_RULES: RouteRule[] = [
  { pattern: /^\/todo$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/request\/new$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/requests\/review$/, roles: ["RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/tasks\/assign$/, roles: ["RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/samples$/, roles: ["RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/profile$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/tasks\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/experiments\/[^/]+$/, roles: ["RND_ENGINEER", "RND_DIRECTOR", "RND_ASSISTANT", ...ADMIN_ROLES] },
  { pattern: /^\/tests\/[^/]+$/, roles: ["TESTER", "QA_TESTER", ...ADMIN_ROLES] },
  { pattern: /^\/shipments\/record$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/shipments\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/pricing$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/pricing\/create\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/pricing\/[^/]+$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/tasks\/[^/]+\/feedback$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES] },
  { pattern: /^\/samples\/[^/]+\/history$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "MANAGER", ...ADMIN_ROLES] },
  { pattern: /^\/403$/, roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER", ...ADMIN_ROLES] },
];

const ACTION_ROLES: Record<AppAction, string[]> = {
  APPROVE_REQUEST: ["RND_DIRECTOR", "MANAGER", ...ADMIN_ROLES],
  ASSIGN_TASK: ["RND_DIRECTOR", ...ADMIN_ROLES],
  ACCEPT_TASK: ["RND_ENGINEER", "RND_DIRECTOR", ...ADMIN_ROLES],
  SAVE_EXPERIMENT: ["RND_ENGINEER", "RND_DIRECTOR", ...ADMIN_ROLES],
  NOTIFY_INTERNAL_TEST: ["RND_ENGINEER", "RND_DIRECTOR", "RND_ASSISTANT", ...ADMIN_ROLES],
  PERFORM_INTERNAL_TEST: ["TESTER", "QA_TESTER", ...ADMIN_ROLES],
  RECORD_SHIPMENT_FEEDBACK: ["RND_ASSISTANT", "RND_DIRECTOR", ...ADMIN_ROLES],
  MANAGE_SETTINGS: [...ADMIN_ROLES],
};

function normalizePath(path: string): string {
  const withoutQuery = path.split("?")[0] ?? path;
  const trimmed = withoutQuery.replace(/\/+$/, "") || "/";
  if (trimmed.startsWith("/admin")) {
    const adminPath = trimmed.slice("/admin".length) || "/";
    return adminPath.replace(/\/+$/, "") || "/";
  }
  if (trimmed.startsWith("/m")) {
    const mobilePath = trimmed.slice("/m".length) || "/";
    return mobilePath.replace(/\/+$/, "") || "/";
  }
  return trimmed;
}

function matchRouteRules(path: string, rules: RouteRule[]): RouteRule | undefined {
  return rules.find((rule) => rule.pattern.test(path));
}

function hasRole(role: string | null | undefined, allowedRoles: string[]): boolean {
  if (!role) return false;
  if (ADMIN_ROLES.includes(role as (typeof ADMIN_ROLES)[number])) {
    return true;
  }
  return allowedRoles.includes(role);
}

export function canAccessRoute(
  role: string | null | undefined,
  path: string,
  platform: "pc" | "mobile" = "pc",
): boolean {
  const normalized = normalizePath(path);
  const rules = platform === "mobile" ? MOBILE_ROUTE_RULES : ROUTE_RULES;
  const rule = matchRouteRules(normalized, rules);
  if (!rule) {
    return true;
  }
  return hasRole(role, rule.roles);
}

export function canPerformAction(role: string | null | undefined, action: AppAction): boolean {
  return hasRole(role, ACTION_ROLES[action]);
}

/** 可作为打样负责人的角色（含总监亲自打样） */
export const RND_ASSIGNEE_ROLE_CODES = ["RND_ENGINEER", "RND_DIRECTOR", "RND"] as const;

export function isRndAssigneeRole(role: string): boolean {
  return (RND_ASSIGNEE_ROLE_CODES as readonly string[]).includes(role);
}

/** 列表默认按负责人过滤（我的打样任务） */
export function shouldFilterTasksByAssignee(role: string): boolean {
  return role === "RND_ENGINEER" || role === "RND_DIRECTOR";
}

export function roleLabel(role: string | null | undefined): string {
  const map: Record<string, string> = {
    RND_ASSISTANT: "研发内勤",
    RND_DIRECTOR: "研发总监",
    RND_ENGINEER: "研发人员",
    TESTER: "测试人员",
    QA_TESTER: "测试人员",
    FINANCE: "财务",
    MANAGER: "管理层",
    ADMIN: "管理员",
    SYSTEM_ADMIN: "超级管理员",
  };
  return role ? map[role] ?? role : "未知";
}
