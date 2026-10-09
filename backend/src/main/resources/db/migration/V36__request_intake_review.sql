create table sample_request_intake (
    request_id varchar(64) primary key references sample_request(id) on delete cascade,
    recorder_user_id varchar(64),
    recorder_name varchar(100),
    version_no integer not null default 0
);
insert into sample_request_intake(request_id, version_no) select id, 0 from sample_request;
create table sample_request_review (
    request_id varchar(64) not null references sample_request(id) on delete cascade,
    version_no integer not null,
    action varchar(32) not null,
    actor_user_id varchar(64) not null,
    actor_name varchar(100) not null,
    comment varchar(2000),
    acted_at timestamp not null,
    primary key(request_id, version_no)
);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'INTAKE-REJECT-' || role_code, role_code, 'POST', '/api/v1/sample-requests/*/reject', true, '驳回需求并附批语', 31, current_timestamp
from role_permission p where http_method='POST' and path_pattern='/api/v1/sample-requests/*/approve' and enabled=true
and not exists(select 1 from role_permission q where q.role_code=p.role_code and q.http_method='POST' and q.path_pattern='/api/v1/sample-requests/*/reject')
group by role_code;
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'INTAKE-RESUBMIT-' || role_code, role_code, 'PUT', '/api/v1/sample-requests/*/resubmit', true, '修改被驳回需求并重新提交', 11, current_timestamp
from role_permission p where http_method='POST' and path_pattern='/api/v1/sample-requests' and enabled=true
and not exists(select 1 from role_permission q where q.role_code=p.role_code and q.http_method='PUT' and q.path_pattern='/api/v1/sample-requests/*/resubmit')
group by role_code;
