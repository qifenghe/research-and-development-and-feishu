delete from role_permission
where role_code = 'RND_DIRECTOR'
  and http_method = 'GET'
  and path_pattern = '/api/v1/settings/users';

insert into role_permission (
    id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
)
select
    'PERM-RND-DIRECTOR-RND-ASSIGNEES-READ',
    'RND_DIRECTOR',
    'GET',
    '/api/v1/rnd-assignees',
    true,
    '查看可分配研发人员',
    95,
    current_timestamp
where not exists (
    select 1
    from role_permission
    where role_code = 'RND_DIRECTOR'
      and http_method = 'GET'
      and path_pattern = '/api/v1/rnd-assignees'
)
and exists (
    select 1
    from role_permission
    where role_code = 'RND_DIRECTOR'
);
