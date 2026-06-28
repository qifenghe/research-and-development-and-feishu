create table experiment_process (
    id varchar(32) primary key,
    experiment_form_id varchar(32) not null,
    sequence integer not null,
    process_name varchar(200) not null,
    before_weight_kg numeric(14, 4),
    after_weight_kg numeric(14, 4),
    loss_rate numeric(10, 6),
    remark varchar(500),
    constraint fk_experiment_process_form foreign key (experiment_form_id) references experiment_form(id)
);

create index idx_experiment_process_form on experiment_process(experiment_form_id);

create table template_config (
    id varchar(32) primary key,
    template_code varchar(100) not null unique,
    template_name varchar(200) not null,
    template_type varchar(50) not null,
    file_path varchar(500),
    version_no varchar(50) not null,
    status varchar(50) not null,
    updated_at timestamp not null
);

insert into template_config (id, template_code, template_name, template_type, file_path, version_no, status, updated_at)
values ('TPL-001', 'PRICING_EXCEL_V1', '核价原料清单模板', 'PRICING_EXCEL', 'templates/pricing-v1.xlsx', 'V1', 'ACTIVE', current_timestamp);
