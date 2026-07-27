export type PricingInboxStatus = "FINANCE_NOTIFIED";

export function pricingInboxStatusForRole(role: string): PricingInboxStatus {
  return "FINANCE_NOTIFIED";
}

export function pricingTodoGroupTitle(role: string): string {
  return role === "FINANCE" ? "待接收核价文件" : "已移交财务核价";
}
