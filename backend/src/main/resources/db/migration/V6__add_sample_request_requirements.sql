alter table sample_request add column application_scenario varchar(500);
alter table sample_request add column flavor_requirement varchar(500);

alter table sample_project add column application_scenario varchar(500);
alter table sample_project add column flavor_requirement varchar(500);

alter table sample_version add column application_scenario varchar(500);
alter table sample_version add column flavor_requirement varchar(500);
