create table material_intake_request (id varchar(100) primary key,payload_json text not null,updated_at timestamp not null);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'MI40-'||r.role||'-'||cast(x.n as varchar),r.role,x.method,x.path,true,'临时物料申请',250+x.n,current_timestamp
from (values ('RND_ENGINEER'),('RND_ASSISTANT'),('RND_DIRECTOR')) r(role)
cross join (values (1,'GET','/api/v1/material-intakes'),(2,'GET','/api/v1/material-intakes/*'),(3,'POST','/api/v1/material-intakes'),(4,'PUT','/api/v1/material-intakes/*'),(5,'POST','/api/v1/material-intakes/*/submit')) x(n,method,path)
where exists(select 1 from role_permission p where p.role_code=r.role)
and not exists(select 1 from role_permission p where p.role_code=r.role and p.http_method=x.method and p.path_pattern=x.path);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'MI40-D-'||cast(x.n as varchar),'RND_DIRECTOR','POST',x.path,true,'总监物料入库审核',260+x.n,current_timestamp
from (values (1,'/api/v1/material-intakes/*/approve'),(2,'/api/v1/material-intakes/*/return')) x(n,path)
where exists(select 1 from role_permission p where p.role_code='RND_DIRECTOR')
and not exists(select 1 from role_permission p where p.role_code='RND_DIRECTOR' and p.http_method='POST' and p.path_pattern=x.path);
