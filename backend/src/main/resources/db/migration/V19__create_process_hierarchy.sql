create table experiment_process_plan (
    id varchar(64) primary key,
    experiment_form_id varchar(64) not null,
    version_no integer not null default 1,
    status varchar(20) not null default 'DRAFT',
    calculation_mode varchar(30) not null default 'PRIMARY_INPUT',
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_process_plan_form unique (experiment_form_id),
    constraint fk_process_plan_form foreign key (experiment_form_id) references experiment_form(id) on delete cascade
);

create table experiment_major_process (
    id varchar(64) primary key,
    process_plan_id varchar(64) not null,
    sequence integer not null,
    process_code varchar(80),
    process_name varchar(200) not null,
    description varchar(1000),
    yield_basis varchar(30) not null default 'PRIMARY_INPUT',
    remark varchar(1000),
    constraint uk_major_process_sequence unique (process_plan_id, sequence),
    constraint fk_major_process_plan foreign key (process_plan_id) references experiment_process_plan(id) on delete cascade
);

create table experiment_minor_step (
    id varchar(64) primary key,
    major_process_id varchar(64) not null,
    sequence integer not null,
    step_code varchar(80),
    step_name varchar(200) not null,
    step_type varchar(30) not null default 'NORMAL',
    parameter_1_name varchar(80),
    parameter_1_value varchar(120),
    parameter_1_unit varchar(30),
    parameter_2_name varchar(80),
    parameter_2_value varchar(120),
    parameter_2_unit varchar(30),
    equipment varchar(300),
    instruction varchar(2000),
    extended_parameters text,
    constraint uk_minor_step_sequence unique (major_process_id, sequence),
    constraint fk_minor_step_major foreign key (major_process_id) references experiment_major_process(id) on delete cascade
);

create table experiment_step_material (
    id varchar(64) primary key,
    minor_step_id varchar(64) not null,
    sequence integer not null,
    material_role varchar(30) not null,
    material_code varchar(100),
    material_name varchar(200) not null,
    material_state varchar(30) not null default 'SOLID',
    weight_kg numeric(14,4),
    formula_material_id varchar(64),
    remark varchar(1000),
    constraint uk_step_material_sequence unique (minor_step_id, sequence),
    constraint fk_step_material_step foreign key (minor_step_id) references experiment_minor_step(id) on delete cascade
);

create table experiment_process_input (
    id varchar(64) primary key,
    major_process_id varchar(64) not null,
    sequence integer not null,
    input_role varchar(30) not null,
    material_code varchar(100),
    material_name varchar(200) not null,
    weight_kg numeric(14,4),
    source_step_material_id varchar(64),
    constraint uk_process_input_sequence unique (major_process_id, sequence),
    constraint fk_process_input_major foreign key (major_process_id) references experiment_major_process(id) on delete cascade
);

create table experiment_process_output (
    id varchar(64) primary key,
    major_process_id varchar(64) not null,
    sequence integer not null,
    output_type varchar(30) not null,
    weight_kg numeric(14,4),
    remark varchar(1000),
    constraint uk_process_output_sequence unique (major_process_id, sequence),
    constraint fk_process_output_major foreign key (major_process_id) references experiment_major_process(id) on delete cascade
);

create index idx_major_process_plan on experiment_major_process(process_plan_id);
create index idx_minor_step_major on experiment_minor_step(major_process_id);
create index idx_step_material_step on experiment_step_material(minor_step_id);
create index idx_process_input_major on experiment_process_input(major_process_id);
create index idx_process_output_major on experiment_process_output(major_process_id);
