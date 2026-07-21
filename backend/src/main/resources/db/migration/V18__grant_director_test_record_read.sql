insert into role_permission (
    id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
)
select
    'PERM-RND-DIRECTOR-TEST-RECORDS-READ',
    'RND_DIRECTOR',
    'GET',
    '/api/v1/rnd-tasks/*/test-records',
    true,
    '查看内部测试记录',
    61,
    current_timestamp
where exists (select 1 from role_permission where role_code = 'RND_DIRECTOR')
  and not exists (
    select 1 from role_permission
    where role_code = 'RND_DIRECTOR'
      and http_method = 'GET'
      and path_pattern = '/api/v1/rnd-tasks/*/test-records'
  );

delete from role_permission
where role_code = 'FINANCE'
  and http_method = 'POST'
  and path_pattern = '/api/v1/pricing-files/*/notify-finance';

delete from role_permission
where role_code = 'RND_ASSISTANT'
  and http_method = 'POST'
  and path_pattern = '/api/v1/pricing-files/*/review';
