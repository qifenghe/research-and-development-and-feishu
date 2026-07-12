alter table pricing_file add column reviewed_by varchar(100);
alter table pricing_file add column reviewed_at timestamp;
alter table pricing_file add column review_comment varchar(500);
alter table pricing_file add column rejection_reason varchar(500);

update pricing_file
set status = 'PENDING_PRICING_REVIEW'
where status = 'GENERATED';
