alter table pricing_file add column reviewed_by varchar(100);
alter table pricing_file add column reviewed_at timestamp;
alter table pricing_file add column review_comment varchar(500);
alter table pricing_file add column rejection_reason varchar(500);

update pricing_file
set status = 'PENDING_PRICING_REVIEW'
where status = 'GENERATED';

insert into role_permission (
    id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
)
select 'PERM-RND-PRICE-LIST-V13', 'RND_ENGINEER', 'GET', '/api/v1/pricing-files', true,
       '查看本人负责产品的核价文件', 76, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/pricing-files'
)
and exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
);

insert into role_permission (
    id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
)
select 'PERM-RND-PRICE-DETAIL-V13', 'RND_ENGINEER', 'GET', '/api/v1/pricing-files/*/detail', true,
       '查看本人负责产品的核价文件详情', 77, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/pricing-files/*/detail'
)
and exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
);

insert into role_permission (
    id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
)
select 'PERM-RND-PRICE-DOWNLOAD-V13', 'RND_ENGINEER', 'GET', '/api/v1/pricing-files/*/download', true,
       '下载本人负责产品的核价文件', 78, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/pricing-files/*/download'
)
and exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
);

update workflow_rule_config
set next_status = 'PENDING_PRICING_REVIEW',
    action_label = '生成核价',
    notify_role = 'RND_DIRECTOR',
    remark = '生成核价文件，进入待审核'
where workflow_code = 'SAMPLE_RND_FLOW'
  and current_status = 'SAMPLE_COMPLETED'
  and action_code = 'REQUEST_PRICING';

update workflow_rule_config
set current_status = 'PRICING_APPROVED',
    action_label = '通知财务',
    remark = '通知财务核价'
where workflow_code = 'SAMPLE_RND_FLOW'
  and current_status = 'PRICING_FILE_GENERATED'
  and action_code = 'NOTIFY_FINANCE';

insert into workflow_rule_config (
    id, workflow_code, current_status, action_code, action_label, next_status,
    enabled, notify_feishu, notify_role, sort_order, remark, updated_at
)
select 'FLOW-PRICE-APPROVE-V13', 'SAMPLE_RND_FLOW', 'PENDING_PRICING_REVIEW', 'APPROVE_PRICING',
       '核价审核通过', 'PRICING_APPROVED', true, true, 'RND_DIRECTOR', 120,
       '产品负责人或研发总监审核通过', current_timestamp
where not exists (
    select 1 from workflow_rule_config
    where workflow_code = 'SAMPLE_RND_FLOW'
      and current_status = 'PENDING_PRICING_REVIEW'
      and action_code = 'APPROVE_PRICING'
);

insert into workflow_rule_config (
    id, workflow_code, current_status, action_code, action_label, next_status,
    enabled, notify_feishu, notify_role, sort_order, remark, updated_at
)
select 'FLOW-PRICE-REJECT-V13', 'SAMPLE_RND_FLOW', 'PENDING_PRICING_REVIEW', 'REJECT_PRICING',
       '核价审核退回', 'PRICING_REJECTED', true, true, 'RND_DIRECTOR', 125,
       '退回后生成新的核价版本', current_timestamp
where not exists (
    select 1 from workflow_rule_config
    where workflow_code = 'SAMPLE_RND_FLOW'
      and current_status = 'PENDING_PRICING_REVIEW'
      and action_code = 'REJECT_PRICING'
);
