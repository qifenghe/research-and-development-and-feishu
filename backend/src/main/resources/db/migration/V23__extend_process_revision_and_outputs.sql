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

alter table experiment_step_material add column source_type varchar(30) not null default 'EXTERNAL';
alter table experiment_step_material add column source_step_output_id varchar(64);
alter table experiment_step_material add constraint fk_step_material_source_output
    foreign key (source_step_output_id) references experiment_step_output(id) on delete cascade;
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
    storage_key varchar(500) not null,
    content_summary text,
    constraint uk_process_artifact_version unique (process_revision_id, artifact_type, document_version),
    constraint fk_process_artifact_revision foreign key (process_revision_id)
        references experiment_process_revision(id) on delete cascade
);

alter table pricing_file add column process_revision_id varchar(64);
alter table pricing_file add constraint fk_pricing_file_process_revision
    foreign key (process_revision_id) references experiment_process_revision(id);

create index idx_step_material_source_output on experiment_step_material(source_step_output_id);
create index idx_control_point_step on experiment_control_point(minor_step_id);
create index idx_control_measurement_point on experiment_control_measurement(control_point_id);
create index idx_process_revision_plan on experiment_process_revision(process_plan_id);
create index idx_process_artifact_revision on experiment_process_artifact(process_revision_id);
create index idx_pricing_file_process_revision on pricing_file(process_revision_id);
