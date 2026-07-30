insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-' || roles.role_code || '-PRICING-PACKAGING-READ', roles.role_code,
       'GET', '/api/v1/pricing-files/*/packaging-items', true, '查看核价包装清单', 116, current_timestamp
from (
    select 'RND_ASSISTANT' as role_code
    union all select 'RND_DIRECTOR'
    union all select 'RND_ENGINEER'
    union all select 'SUPER_ADMIN'
) roles
where exists (select 1 from role_permission existing_role where existing_role.role_code = roles.role_code)
  and not exists (
    select 1 from role_permission existing_permission
    where existing_permission.role_code = roles.role_code
      and existing_permission.http_method = 'GET'
      and existing_permission.path_pattern = '/api/v1/pricing-files/*/packaging-items'
  );

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-' || roles.role_code || '-PRICING-PACKAGING-CONFIRM', roles.role_code,
       'PUT', '/api/v1/pricing-files/*/packaging-items', true, '确认核价包装清单', 117, current_timestamp
from (
    select 'RND_DIRECTOR' as role_code
    union all select 'RND_ENGINEER'
    union all select 'SUPER_ADMIN'
) roles
where exists (select 1 from role_permission existing_role where existing_role.role_code = roles.role_code)
  and not exists (
    select 1 from role_permission existing_permission
    where existing_permission.role_code = roles.role_code
      and existing_permission.http_method = 'PUT'
      and existing_permission.path_pattern = '/api/v1/pricing-files/*/packaging-items'
  );
