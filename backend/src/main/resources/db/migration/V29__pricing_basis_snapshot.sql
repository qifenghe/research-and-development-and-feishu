create table pricing_basis_snapshot (
 pricing_file_id varchar(64) primary key references pricing_file(id),
 draft_json text not null,
 version_no integer not null,
 confirmed_by varchar(100),
 confirmed_at varchar(40)
);

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select concat('PB29-',r.role_code,'-',a.suffix),r.role_code,a.method,concat('/api/v1/pricing-files/*/basis',a.path),true,'核价数据确认',80,current_timestamp
from (select distinct role_code from role_permission where role_code in ('RND_DIRECTOR','RND_ENGINEER','RND_ASSISTANT','FINANCE','MANAGER','ADMIN','SYSTEM_ADMIN','SUPER_ADMIN')) r
cross join (select 'GET' method,'' path,'READ' suffix union all select 'PUT','','SAVE' union all select 'POST','/preview','PREVIEW' union all select 'POST','/confirm','CONFIRM' union all select 'POST','/generate','GENERATE') a
where a.method='GET' or r.role_code in ('RND_DIRECTOR','RND_ENGINEER','SUPER_ADMIN');
