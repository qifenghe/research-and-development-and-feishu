alter table test_assignment add column process_revision_id varchar(64);
alter table test_assignment add column tester_user_id varchar(64);
alter table test_assignment add column archived_at timestamp;
alter table test_assignment add column active_marker varchar(64) not null default 'ACTIVE';
alter table test_assignment add constraint fk_test_assignment_process_revision
    foreign key (process_revision_id) references experiment_process_revision(id);
alter table test_assignment add constraint fk_test_assignment_tester_user
    foreign key (tester_user_id) references user_account(id);
-- Preserve every legacy assignment (and its test_record FK).  A form may have been sent
-- to test more than once before this release; retain the latest assignment as active and
-- explicitly archive the rest before enforcing the single-active-assignment invariant.
update test_assignment set active_marker = id;
update test_assignment assignment
set active_marker = 'ACTIVE'
where assignment.id = (
    select candidate.id
    from test_assignment candidate
    where candidate.experiment_form_id = assignment.experiment_form_id
    order by candidate.assigned_at desc, candidate.id desc
    limit 1
);
update test_assignment set archived_at = current_timestamp, status = 'ARCHIVED'
where active_marker <> 'ACTIVE';
alter table test_assignment add constraint uk_test_assignment_experiment_form unique (experiment_form_id, active_marker);
create index idx_test_assignment_process_revision on test_assignment(process_revision_id);
create index idx_test_assignment_tester_user on test_assignment(tester_user_id);

update role_permission
set enabled = false, updated_at = current_timestamp
where role_code = 'RND_ASSISTANT'
  and http_method = 'POST'
  and path_pattern = '/api/v1/experiment-forms/*/submit-test';

insert into role_permission (id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at)
select 'PERM-RND-DIR-DEVIATION-24', 'RND_DIRECTOR', 'POST',
       '/api/v1/experiment-forms/*/process-plan/control-points/*/confirm-deviation', true, '确认极重要工艺偏差', 78, current_timestamp
where not exists (
    select 1 from role_permission
    where role_code = 'RND_DIRECTOR'
      and http_method = 'POST'
      and path_pattern = '/api/v1/experiment-forms/*/process-plan/control-points/*/confirm-deviation'
)
and exists (select 1 from role_permission where role_code = 'RND_DIRECTOR');
