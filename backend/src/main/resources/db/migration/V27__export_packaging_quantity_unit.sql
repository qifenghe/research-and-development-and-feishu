alter table pricing_packaging_item add column quantity_unit varchar(40);
alter table packaging_template_item add column quantity_unit varchar(40);

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-0', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/export-check');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-1', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/export-preview', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/export-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-2', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/trials/*/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/trials/*/export-check');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-3', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/trials/*/export-preview', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/trials/*/export-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-4', 'RND_DIRECTOR', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_DIRECTOR')
and not exists (select 1 from role_permission where role_code='RND_DIRECTOR' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/export-check');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-5', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/export-check');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-6', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/export-preview', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/export-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-7', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/trials/*/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/trials/*/export-check');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-8', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/trials/*/export-preview', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/trials/*/export-preview');

insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select 'PERM-EXPORT-27-9', 'RND_ENGINEER', 'GET', '/api/v1/experiment-forms/*/process-plan/revisions/*/export-check', true, '查看研发导出检查与预览', 78, current_timestamp
where exists (select 1 from role_permission where role_code='RND_ENGINEER')
and not exists (select 1 from role_permission where role_code='RND_ENGINEER' and http_method='GET' and path_pattern='/api/v1/experiment-forms/*/process-plan/revisions/*/export-check');
