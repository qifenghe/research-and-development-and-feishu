create table experiment_trial_scheme (
    id varchar(64) primary key,
    experiment_form_id varchar(64) not null,
    version_no integer not null default 1,
    name varchar(120) not null,
    source_trial_id varchar(64),
    archived boolean not null default false,
    purpose varchar(1000),
    variables text,
    conclusion varchar(20) not null default 'PENDING',
    recommendation_reason varchar(1000),
    quality_score numeric(4,2),
    quality_notes varchar(2000),
    difficulty varchar(20),
    plan_json text not null,
    planned_data_json text not null,
    inherited_actuals boolean not null default false,
    inherited_measurement_ids_json text not null,
    inherited_measurement_fingerprints_json text not null,
    major_origins_json text not null,
    created_by varchar(100) not null,
    created_by_user_id varchar(64) not null,
    created_at timestamp not null,
    updated_by varchar(100) not null,
    updated_by_user_id varchar(64) not null,
    updated_at timestamp not null,
    constraint fk_trial_scheme_form foreign key (experiment_form_id) references experiment_form(id) on delete cascade,
    constraint fk_trial_scheme_source foreign key (source_trial_id) references experiment_trial_scheme(id) on delete set null,
    constraint chk_trial_scheme_conclusion check (conclusion in ('PENDING', 'ADJUST', 'REJECT', 'RECOMMEND')),
    constraint chk_trial_scheme_difficulty check (difficulty is null or difficulty in ('EASY', 'MEDIUM', 'HARD')),
    constraint chk_trial_scheme_quality check (quality_score is null or (quality_score >= 0 and quality_score <= 10))
);

create index idx_trial_scheme_form on experiment_trial_scheme(experiment_form_id, archived, updated_at);

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-LIST-25', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/trials', true, '查看试验方案', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/trials')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-DETAIL-25', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/trials/*', true, '查看试验方案详情', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/trials/*')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-CREATE-25', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials', true, '创建试验方案', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-COPY-25', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/copy', true, '复制试验方案', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials/*/copy')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-ARCHIVE-25', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/trials/*/archive', true, '归档或恢复试验方案', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials/*/archive')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-TRIAL-PUT-25', 'RND_DIRECTOR', 'PUT', '/api/v1/experiment-forms/*/trials/*', true, '编辑试验方案', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'PUT' and path_pattern = '/api/v1/experiment-forms/*/trials/*')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-LIST-25', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/trials', true, '查看试验方案', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/trials')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-DETAIL-25', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/trials/*', true, '查看试验方案详情', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/trials/*')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-CREATE-25', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials', true, '创建试验方案', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-COPY-25', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials/*/copy', true, '复制试验方案', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials/*/copy')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-ARCHIVE-25', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/trials/*/archive', true, '归档或恢复试验方案', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/trials/*/archive')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-TRIAL-PUT-25', 'RND_ENGINEER', 'PUT', '/api/v1/experiment-forms/*/trials/*', true, '编辑试验方案', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'PUT' and path_pattern = '/api/v1/experiment-forms/*/trials/*')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
