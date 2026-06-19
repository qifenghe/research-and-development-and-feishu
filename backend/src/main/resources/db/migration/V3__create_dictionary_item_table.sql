create table dictionary_item (
    id varchar(32) primary key,
    category varchar(100) not null,
    item_code varchar(100) not null,
    item_label varchar(200) not null,
    enabled boolean not null,
    sort_order integer not null default 0,
    remark varchar(500),
    updated_at timestamp not null,
    constraint uk_dictionary_item_category_code unique (category, item_code)
);

create index idx_dictionary_item_category_enabled
    on dictionary_item(category, enabled, sort_order);
