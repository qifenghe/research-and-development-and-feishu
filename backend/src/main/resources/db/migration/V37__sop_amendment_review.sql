create table sop_amendment (
 id varchar(64) primary key,
 form_id varchar(64) not null references experiment_form(id),
 source_revision_id varchar(64) not null references experiment_process_revision(id),
 source_hash varchar(64) not null,
 version_no integer not null default 0,
 status varchar(32) not null,
 reason varchar(2000) not null,
 evidence_note varchar(2000),
 created_by_id varchar(64) not null,
 created_by varchar(100) not null,
 created_at timestamp not null,
 updated_at timestamp not null,
 snapshot_json text not null,
 snapshot_hash varchar(64) not null,
 submitted_hash varchar(64),
 published_revision_id varchar(64) references experiment_process_revision(id)
);
create table sop_amendment_active (
 form_id varchar(64) primary key references experiment_form(id),
 amendment_id varchar(64) not null unique references sop_amendment(id)
);
create table sop_amendment_history (
 amendment_id varchar(64) not null references sop_amendment(id),
 version_no integer not null,
 action varchar(32) not null,
 actor_id varchar(64) not null,
 actor_name varchar(100) not null,
 comment varchar(2000),
 acted_at timestamp not null,
 primary key(amendment_id,version_no)
);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'SOP37-A-'||cast(x.n as varchar), 'RND_ASSISTANT',x.method,x.path,true,'SOP修订权限',220+x.n,current_timestamp
from (values (1,'GET','/api/v1/sops'),(2,'GET','/api/v1/sop-amendments'),(3,'GET','/api/v1/sop-amendments/*'),(4,'POST','/api/v1/sop-amendments'),(5,'PUT','/api/v1/sop-amendments/*'),(6,'POST','/api/v1/sop-amendments/*/submit'),(7,'POST','/api/v1/sop-amendments/*/cancel'),(8,'GET','/api/v1/sops/*/revisions/*'),(9,'GET','/api/v1/sops/*/revisions/*/artifacts'),(10,'POST','/api/v1/sops/*/revisions/*/artifacts'),(11,'GET','/api/v1/sops/*/revisions/*/artifacts/*/download')) x(n,method,path)
where exists(select 1 from role_permission p where p.role_code='RND_ASSISTANT' and p.http_method='POST' and p.path_pattern='/api/v1/sample-requests' and p.enabled=true)
and not exists(select 1 from role_permission p where p.role_code='RND_ASSISTANT' and p.http_method=x.method and p.path_pattern=x.path);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'SOP37-D-'||cast(x.n as varchar), 'RND_DIRECTOR',x.method,x.path,true,'SOP审核权限',220+x.n,current_timestamp
from (values (1,'GET','/api/v1/sop-amendments'),(2,'GET','/api/v1/sop-amendments/*'),(3,'POST','/api/v1/sop-amendments/*/return'),(4,'POST','/api/v1/sop-amendments/*/approve'),(5,'GET','/api/v1/sops/*/revisions/*'),(6,'GET','/api/v1/sops/*/revisions/*/artifacts'),(7,'POST','/api/v1/sops/*/revisions/*/artifacts'),(8,'GET','/api/v1/sops/*/revisions/*/artifacts/*/download')) x(n,method,path)
where exists(select 1 from role_permission p where p.role_code='RND_DIRECTOR' and p.http_method='GET' and p.path_pattern='/api/v1/sops' and p.enabled=true)
and not exists(select 1 from role_permission p where p.role_code='RND_DIRECTOR' and p.http_method=x.method and p.path_pattern=x.path);
