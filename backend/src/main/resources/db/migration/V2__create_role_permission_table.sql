create table role_permission (
    id varchar(32) primary key,
    role_code varchar(100) not null,
    http_method varchar(20) not null,
    path_pattern varchar(300) not null,
    enabled boolean not null,
    description varchar(500),
    sort_order integer not null default 0,
    updated_at timestamp not null
);

create index idx_role_permission_role on role_permission(role_code, enabled);
create index idx_role_permission_method_path on role_permission(http_method, path_pattern);
