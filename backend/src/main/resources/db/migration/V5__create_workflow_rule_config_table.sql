create table workflow_rule_config (
    id varchar(32) primary key,
    workflow_code varchar(100) not null,
    current_status varchar(100) not null,
    action_code varchar(100) not null,
    action_label varchar(200) not null,
    next_status varchar(100) not null,
    enabled boolean not null,
    notify_feishu boolean not null,
    notify_role varchar(100),
    sort_order integer not null default 0,
    remark varchar(500),
    updated_at timestamp not null,
    constraint uk_workflow_rule_config_status_action unique (workflow_code, current_status, action_code)
);

create index idx_workflow_rule_config_workflow_enabled
    on workflow_rule_config(workflow_code, enabled, sort_order);
