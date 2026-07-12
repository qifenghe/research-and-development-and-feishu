export type PricingInboxStatus = "PRICING_APPROVED" | "FINANCE_NOTIFIED";

export function pricingInboxStatusForRole(role: string): PricingInboxStatus {
  return role === "FINANCE" ? "FINANCE_NOTIFIED" : "PRICING_APPROVED";
}

export function pricingTodoGroupTitle(role: string): string {
  return role === "FINANCE" ? "待接收核价文件" : "待生成核价";
}
