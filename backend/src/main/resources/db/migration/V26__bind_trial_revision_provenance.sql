create table experiment_trial_submission_preview (
    token varchar(64) primary key,
    experiment_form_id varchar(64) not null references experiment_form(id) on delete cascade,
    trial_id varchar(64) not null references experiment_trial_scheme(id) on delete cascade,
    trial_version_no integer not null,
    process_version_no integer not null,
    snapshot_hash varchar(64) not null,
    created_by_user_id varchar(64) not null,
    created_at timestamp not null
);

create table experiment_trial_promotion (
    id varchar(64) primary key,
    experiment_form_id varchar(64) not null references experiment_form(id) on delete cascade,
    trial_id varchar(64) not null references experiment_trial_scheme(id),
    trial_version_no integer not null,
    trial_name varchar(120) not null,
    trial_snapshot_json text not null,
    trial_snapshot_hash varchar(64) not null,
    displaced_draft_json text,
    idempotency_key varchar(120) not null,
    request_hash varchar(64) not null,
    preview_token varchar(64) not null references experiment_trial_submission_preview(token),
    revision_id varchar(64) not null references experiment_process_revision(id) on delete cascade,
    promoted_by varchar(100) not null,
    promoted_by_user_id varchar(64) not null,
    promoted_at timestamp not null,
    constraint uq_trial_promotion_request unique (experiment_form_id, idempotency_key),
    constraint uq_trial_promotion_revision unique (revision_id)
);

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-0', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/submission-preview', true, '预览试验晋升', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/submission-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-1', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/submit', true, '正式提交试验方案', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/submit');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-2', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/control-points/*/confirm', true, '确认试验实测', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/control-points/*/confirm');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-3', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/source', true, '查看正式版本试验来源', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/source');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-4', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/displaced-draft', true, '找回晋升前草稿', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/displaced-draft');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-5', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/control-points/*/confirm-deviation', true, '确认试验偏差', 77, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/control-points/*/confirm-deviation');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-6', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials/*/submission-preview', true, '预览试验晋升', 17, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/submission-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-7', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials/*/submit', true, '正式提交试验方案', 17, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/submit');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-8', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials/*/control-points/*/confirm', true, '确认试验实测', 17, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='POST' and path_pattern='/api/v1/experiment-forms/*/trials/*/control-points/*/confirm');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-9', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/source', true, '查看正式版本试验来源', 17, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/source');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-10', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/displaced-draft', true, '找回晋升前草稿', 17, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/displaced-draft');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-11', 'TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/source', true, '查看正式版本试验来源', 11, current_timestamp
where exists (select 1 from role_permission where role_code='TESTER')
and not exists (select 1 from role_permission where role_code='TESTER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/source');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-TRIAL-26-12', 'QA_TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/source', true, '查看正式版本试验来源', 11, current_timestamp
where exists (select 1 from role_permission where role_code='QA_TESTER')
and not exists (select 1 from role_permission where role_code='QA_TESTER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/source');
