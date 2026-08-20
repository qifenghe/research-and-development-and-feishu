create table experiment_step_output (
    id varchar(64) primary key,
    minor_step_id varchar(64) not null,
    sequence integer not null,
    output_type varchar(30) not null,
    output_name varchar(200) not null,
    material_state varchar(30) not null default 'SEMI_SOLID',
    weight_kg numeric(14,4),
    primary_output boolean not null default false,
    continue_flow boolean not null default true,
    remark varchar(1000),
    constraint uk_step_output_sequence unique (minor_step_id, sequence),
    constraint fk_step_output_step foreign key (minor_step_id)
        references experiment_minor_step(id) on delete cascade
);

-- Formal process identifiers and audit context may both legitimately use the full form-id/reason sizes.
-- Drop/recreate form foreign keys around the type widening so this V22 -> V23 migration is valid in H2 and PostgreSQL.
alter table experiment_material drop constraint fk_experiment_material_form;
alter table test_assignment drop constraint fk_test_assignment_form;
alter table test_record drop constraint fk_test_record_form;
alter table experiment_process drop constraint fk_experiment_process_form;
alter table experiment_process_plan drop constraint fk_process_plan_form;
alter table experiment_form alter column id type varchar(64);
alter table experiment_material alter column experiment_form_id type varchar(64);
alter table test_assignment alter column experiment_form_id type varchar(64);
alter table test_record alter column experiment_form_id type varchar(64);
alter table experiment_process alter column experiment_form_id type varchar(64);
alter table experiment_material add constraint fk_experiment_material_form foreign key (experiment_form_id) references experiment_form(id);
alter table test_assignment add constraint fk_test_assignment_form foreign key (experiment_form_id) references experiment_form(id);
alter table test_record add constraint fk_test_record_form foreign key (experiment_form_id) references experiment_form(id);
alter table experiment_process add constraint fk_experiment_process_form foreign key (experiment_form_id) references experiment_form(id);
alter table experiment_process_plan add constraint fk_process_plan_form foreign key (experiment_form_id) references experiment_form(id) on delete cascade;
alter table audit_log alter column business_id type varchar(64);
alter table audit_log alter column detail type text;
alter table audit_log add column operator_user_id varchar(64);

create table process_artifact_cleanup_ledger (
    storage_key varchar(500) primary key,
    created_at timestamp not null,
    last_attempt timestamp,
    error varchar(1000)
);

alter table experiment_process_plan add column balance_tolerance_kg numeric(14,4) not null default 0.0100;
alter table experiment_process_plan add column source_revision_id varchar(64);
alter table experiment_process_plan add column change_reason varchar(1000);

alter table experiment_step_material add column source_type varchar(30) not null default 'EXTERNAL';
alter table experiment_step_material add column source_step_output_id varchar(64);
alter table experiment_step_material add constraint fk_step_material_source_output
    foreign key (source_step_output_id) references experiment_step_output(id);
alter table experiment_step_material add constraint chk_step_material_source_type
    check (source_type in ('EXTERNAL', 'STEP_OUTPUT'));
alter table experiment_step_material add constraint chk_step_material_source_consistency
    check (
        (source_type = 'EXTERNAL' and source_step_output_id is null)
        or (
            source_type = 'STEP_OUTPUT'
            and source_step_output_id is not null
            and material_code is null
            and formula_material_id is null
        )
    );

create table experiment_control_point (
    id varchar(64) primary key,
    minor_step_id varchar(64) not null,
    sequence integer not null,
    control_type varchar(30) not null,
    importance varchar(30) not null default 'NORMAL',
    item_name varchar(200) not null,
    target_value numeric(14,4),
    lower_limit numeric(14,4),
    upper_limit numeric(14,4),
    unit varchar(30),
    method varchar(300),
    measurement_tool varchar(300),
    frequency varchar(120),
    deviation_action varchar(1000),
    resolved boolean not null default false,
    confirmed_by varchar(100),
    confirmed_at timestamp,
    basis_or_remark varchar(1000),
    constraint uk_control_point_sequence unique (minor_step_id, sequence),
    constraint fk_control_point_step foreign key (minor_step_id)
        references experiment_minor_step(id) on delete cascade
);

create table experiment_control_measurement (
    id varchar(64) primary key,
    control_point_id varchar(64) not null,
    sequence integer not null,
    measured_value numeric(14,4),
    measured_at timestamp,
    result varchar(30) not null default 'PENDING',
    deviation_action varchar(1000),
    retest_result varchar(30),
    remark varchar(1000),
    constraint uk_control_measurement_sequence unique (control_point_id, sequence),
    constraint fk_control_measurement_point foreign key (control_point_id)
        references experiment_control_point(id) on delete cascade
);

create table experiment_process_revision (
    id varchar(64) primary key,
    process_plan_id varchar(64) not null,
    experiment_form_id varchar(64) not null,
    revision_no integer not null,
    source_revision_id varchar(64),
    change_reason varchar(1000),
    submitted_by varchar(100),
    submitted_at timestamp not null,
    snapshot_json text not null,
    snapshot_hash varchar(64) not null,
    constraint uk_process_revision_no unique (experiment_form_id, revision_no),
    constraint fk_process_revision_plan foreign key (process_plan_id)
        references experiment_process_plan(id)
);

create table experiment_process_artifact (
    id varchar(64) primary key,
    process_revision_id varchar(64) not null,
    artifact_type varchar(30) not null,
    document_version varchar(100) not null,
    status varchar(30) not null,
    generated_at timestamp not null,
    generated_by varchar(100),
    generated_by_user_id varchar(64),
    storage_key varchar(500),
    content_sha256 varchar(64),
    byte_size bigint,
    content_summary text,
    failure_reason varchar(1000),
    constraint uk_process_artifact_version unique (process_revision_id, artifact_type, document_version),
    constraint fk_process_artifact_revision foreign key (process_revision_id)
        references experiment_process_revision(id) on delete cascade
);

alter table rnd_task add column assignee_user_id varchar(64);
alter table rnd_task add constraint fk_rnd_task_assignee_user foreign key (assignee_user_id) references user_account(id);

alter table pricing_file add column process_revision_id varchar(64);
alter table pricing_file add constraint fk_pricing_file_process_revision
    foreign key (process_revision_id) references experiment_process_revision(id);

create index idx_step_material_source_output on experiment_step_material(source_step_output_id);
create index idx_control_point_step on experiment_control_point(minor_step_id);
create index idx_control_measurement_point on experiment_control_measurement(control_point_id);
create index idx_process_revision_plan on experiment_process_revision(process_plan_id);
create index idx_process_artifact_revision on experiment_process_artifact(process_revision_id);
create index idx_pricing_file_process_revision on pricing_file(process_revision_id);

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-SUBMIT-CHECK-23', 'RND_DIRECTOR', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 76, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_DIRECTOR'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-SUBMIT-CHECK-23', 'RND_ENGINEER', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 16, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TEST-SUBMIT-CHECK-23', 'TESTER', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 11, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'TESTER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'TESTER');

-- Formal-revision artifacts are intentionally explicit: read-only testing roles can inspect/download,
-- while only R&D editors can generate a fresh immutable file version.
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PART-LIST-23', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '查看工艺成果文件', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PART-DOWN-23', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download', true, '下载工艺成果文件', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PART-GEN-23', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '生成工艺成果文件', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PART-LIST-23', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '查看工艺成果文件', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PART-DOWN-23', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download', true, '下载工艺成果文件', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PART-GEN-23', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '生成工艺成果文件', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TEST-PART-LIST-23', 'TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '查看工艺成果文件', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'TESTER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TEST-PART-DOWN-23', 'TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download', true, '下载工艺成果文件', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download')
and exists (select 1 from role_permission where role_code = 'TESTER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-QA-PART-LIST-23', 'QA_TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts', true, '查看工艺成果文件', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'QA_TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts')
and exists (select 1 from role_permission where role_code = 'QA_TESTER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-QA-PART-DOWN-23', 'QA_TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download', true, '下载工艺成果文件', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'QA_TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/artifacts/*/download')
and exists (select 1 from role_permission where role_code = 'QA_TESTER');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PREV-LIST-23', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions', true, '查看工艺正式版本', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PREV-DETAIL-23', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*', true, '查看工艺正式版本详情', 76, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PREV-SUBMIT-23', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/process-plan/submit', true, '正式提交工艺', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/submit')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-PREV-DRAFT-23', 'RND_DIRECTOR', 'POST', '/api/v1/experiment-forms/*/process-plan/revisions/*/new-draft', true, '从正式版本创建工艺草稿', 77, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_DIRECTOR' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/new-draft')
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PREV-LIST-23', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions', true, '查看工艺正式版本', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PREV-DETAIL-23', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*', true, '查看工艺正式版本详情', 16, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PREV-SUBMIT-23', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/process-plan/submit', true, '正式提交工艺', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/submit')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENG-PREV-DRAFT-23', 'RND_ENGINEER', 'POST', '/api/v1/experiment-forms/*/process-plan/revisions/*/new-draft', true, '从正式版本创建工艺草稿', 17, current_timestamp
where not exists (select 1 from role_permission where role_code = 'RND_ENGINEER' and http_method = 'POST' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*/new-draft')
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TEST-PREV-LIST-23', 'TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions', true, '查看工艺正式版本', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions')
and exists (select 1 from role_permission where role_code = 'TESTER');
insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TEST-PREV-DETAIL-23', 'TESTER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*', true, '查看工艺正式版本详情', 11, current_timestamp
where not exists (select 1 from role_permission where role_code = 'TESTER' and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions/*')
and exists (select 1 from role_permission where role_code = 'TESTER');
