create table packaging_template_item (
    id varchar(32) primary key,
    template_code varchar(100) not null,
    sequence integer not null,
    material_code varchar(128),
    material_name varchar(255) not null,
    conversion_type varchar(32) not null,
    units_per_parent numeric(18, 6) not null,
    package_spec varchar(255),
    remark varchar(1000),
    enabled boolean not null default true,
    constraint uk_packaging_template_item unique (template_code, sequence)
);

create table pricing_packaging_item (
    id varchar(32) primary key,
    pricing_file_id varchar(32) not null,
    sequence integer not null,
    source varchar(32) not null,
    material_code varchar(128),
    material_name varchar(255) not null,
    quantity numeric(18, 6),
    package_spec varchar(255),
    conversion_rule varchar(255),
    remark varchar(1000),
    confirmation_status varchar(32) not null,
    modification_reason varchar(1000),
    constraint fk_pricing_packaging_item_file foreign key (pricing_file_id) references pricing_file(id),
    constraint uk_pricing_packaging_item_sequence unique (pricing_file_id, sequence)
);

create index idx_pricing_packaging_item_file on pricing_packaging_item(pricing_file_id);
create index idx_packaging_template_item_code on packaging_template_item(template_code, enabled);

insert into packaging_template_item (
    id, template_code, sequence, material_code, material_name, conversion_type, units_per_parent, package_spec, remark, enabled
) values
    ('PKG-TPL-001', 'DEFAULT_BAG', 10, 'FBZ-BAG', '通用内袋', 'PER_BAG', 1, '1 个/袋', '请按实际包装确认', true),
    ('PKG-TPL-002', 'DEFAULT_BAG', 20, 'FBZ-CARTON', '通用外箱', 'PER_BOX', 1, '按每箱袋数换算', '请按实际包装确认', true),
    ('PKG-TPL-003', 'DEFAULT_BAG', 30, 'FBZ-TAPE', '透明胶带', 'PER_BOX', 1, '1 卷/箱', '请按实际包装确认', true);
