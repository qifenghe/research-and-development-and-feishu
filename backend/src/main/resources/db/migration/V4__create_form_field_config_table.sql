create table form_field_config (
    id varchar(32) primary key,
    form_code varchar(100) not null,
    field_code varchar(100) not null,
    field_label varchar(200) not null,
    control_type varchar(50) not null,
    required boolean not null,
    enabled boolean not null,
    sort_order integer not null default 0,
    dictionary_category varchar(100),
    placeholder varchar(300),
    default_value varchar(300),
    remark varchar(500),
    updated_at timestamp not null,
    constraint uk_form_field_config_form_field unique (form_code, field_code)
);

create index idx_form_field_config_form_enabled
    on form_field_config(form_code, enabled, sort_order);
