alter table experiment_minor_step add column parameters_json clob;
alter table experiment_step_material add column addition_timing varchar(2000);
alter table experiment_step_material add column addition_method varchar(2000);
