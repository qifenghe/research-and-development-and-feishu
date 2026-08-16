update workflow_rule_config
set next_status = 'FINANCE_NOTIFIED',
    action_label = '核价审核通过并移交财务',
    notify_feishu = true,
    notify_role = 'FINANCE',
    remark = '产品负责人或研发总监审核通过后自动移交财务',
    updated_at = current_timestamp
where workflow_code = 'SAMPLE_RND_FLOW'
  and current_status = 'PENDING_PRICING_REVIEW'
  and action_code = 'APPROVE_PRICING';

delete from workflow_rule_config
where workflow_code = 'SAMPLE_RND_FLOW'
  and current_status = 'PRICING_APPROVED'
  and action_code = 'NOTIFY_FINANCE';
