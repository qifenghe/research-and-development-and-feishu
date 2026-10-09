-- Add the index only for roles already allowed to read formal revision lists.
-- Do not restore any revoked formal-read or export permissions.
insert into role_permission(id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'SOP-INDEX-' || role_code, role_code, 'GET', '/api/v1/sops', true, '查看SOP列表', 75, current_timestamp
from role_permission
where role_code in ('RND_DIRECTOR', 'RND_ENGINEER', 'TESTER', 'QA_TESTER')
  and http_method = 'GET' and path_pattern = '/api/v1/experiment-forms/*/process-plan/revisions' and enabled = true
  and not exists(select 1 from role_permission p where p.role_code = role_permission.role_code and p.path_pattern = '/api/v1/sops')
group by role_code;
