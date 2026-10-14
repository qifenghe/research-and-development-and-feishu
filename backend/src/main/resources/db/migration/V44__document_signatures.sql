alter table experiment_process_revision add column compiled_by varchar(100);
alter table experiment_process_revision add column compiled_at timestamp;
alter table experiment_process_revision add column reviewed_by varchar(100);
alter table experiment_process_revision add column reviewed_at timestamp;
alter table pricing_file add column compiled_by varchar(100);
alter table pricing_file add column compiled_at timestamp;
update experiment_process_revision set compiled_by=submitted_by, compiled_at=submitted_at;
-- Only published amendments carry an explicit director approval. Do not infer approval from a name or current role.
update experiment_process_revision r set
 compiled_by=(select h.actor_name from sop_amendment a join sop_amendment_history h on h.amendment_id=a.id where a.published_revision_id=r.id and h.action='SUBMITTED' order by h.version_no desc limit 1),
 compiled_at=(select h.acted_at from sop_amendment a join sop_amendment_history h on h.amendment_id=a.id where a.published_revision_id=r.id and h.action='SUBMITTED' order by h.version_no desc limit 1),
 reviewed_by=(select h.actor_name from sop_amendment a join sop_amendment_history h on h.amendment_id=a.id where a.published_revision_id=r.id and h.action='PUBLISHED'),
 reviewed_at=(select h.acted_at from sop_amendment a join sop_amendment_history h on h.amendment_id=a.id where a.published_revision_id=r.id and h.action='PUBLISHED')
where exists(select 1 from sop_amendment a where a.published_revision_id=r.id);
