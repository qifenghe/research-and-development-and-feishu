create table material_catalog (
 id varchar(64) primary key,
 code varchar(100) not null unique,
 name varchar(200) not null,
 category varchar(30) not null,
 specification varchar(300),
 unit varchar(20) not null,
 status varchar(20) not null,
 version integer not null default 0,
 created_at timestamp not null,
 updated_at timestamp not null
);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select concat('MC30-',r.role_code,'-',a.suffix),r.role_code,a.method,concat('/api/v1/materials',a.path),true,a.description,210,current_timestamp
from (select distinct role_code from role_permission where role_code in ('RND_ENGINEER','RND_DIRECTOR','RND_ASSISTANT')) r
cross join (select 'GET' method,'' path,'READ' suffix,'查询物料库' description union all select 'POST','','CREATE','新增物料' union all select 'PUT','/*','EDIT','编辑物料' union all select 'POST','/*/status','STATUS','启停物料') a
where a.method='GET' or r.role_code='RND_ASSISTANT';
