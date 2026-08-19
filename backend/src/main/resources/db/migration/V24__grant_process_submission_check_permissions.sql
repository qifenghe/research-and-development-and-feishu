insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIRECTOR-PROCESS-SUBMISSION-CHECK-V24', 'RND_DIRECTOR', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 76, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_DIRECTOR'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-ENGINEER-PROCESS-SUBMISSION-CHECK-V24', 'RND_ENGINEER', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 16, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_ENGINEER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'RND_ENGINEER');

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-TESTER-PROCESS-SUBMISSION-CHECK-V24', 'TESTER', 'GET',
       '/api/v1/experiment-forms/*/process-plan/submission-check', true, '查看工艺提交检查', 11, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'TESTER'
      and http_method = 'GET'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/submission-check'
)
and exists (select 1 from role_permission where role_code = 'TESTER');
