insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-' || roles.role_code || '-TEST-PASS', roles.role_code, 'POST', '/api/v1/test-assignments/*/pass', true, '提交内部测试通过', 500, current_timestamp
from (
    select 'RND_ASSISTANT' as role_code
    union all select 'RND_DIRECTOR'
    union all select 'RND_ENGINEER'
    union all select 'TESTER'
    union all select 'QA_TESTER'
    union all select 'FINANCE'
    union all select 'MANAGER'
) roles
where exists (select 1 from role_permission existing_role where existing_role.role_code = roles.role_code)
  and not exists (
    select 1 from role_permission existing_permission
    where existing_permission.role_code = roles.role_code
      and existing_permission.http_method = 'POST'
      and existing_permission.path_pattern = '/api/v1/test-assignments/*/pass'
  );

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-' || roles.role_code || '-TEST-RESAMPLE', roles.role_code, 'POST', '/api/v1/test-assignments/*/fail-resample', true, '提交内部测试复打样', 501, current_timestamp
from (
    select 'RND_ASSISTANT' as role_code
    union all select 'RND_DIRECTOR'
    union all select 'RND_ENGINEER'
    union all select 'TESTER'
    union all select 'QA_TESTER'
    union all select 'FINANCE'
    union all select 'MANAGER'
) roles
where exists (select 1 from role_permission existing_role where existing_role.role_code = roles.role_code)
  and not exists (
    select 1 from role_permission existing_permission
    where existing_permission.role_code = roles.role_code
      and existing_permission.http_method = 'POST'
      and existing_permission.path_pattern = '/api/v1/test-assignments/*/fail-resample'
  );
