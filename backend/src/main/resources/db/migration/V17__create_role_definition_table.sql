create table role_definition (
    role_code varchar(64) primary key,
    role_name varchar(128) not null,
    description varchar(500),
    status varchar(16) not null,
    system_builtin boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_role_definition_role_name on role_definition(role_name);
