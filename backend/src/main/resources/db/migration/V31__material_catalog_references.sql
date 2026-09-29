alter table experiment_step_material add column catalog_material_id varchar(64);
alter table experiment_step_material add column catalog_specification varchar(300);
alter table experiment_step_material add column catalog_unit varchar(20);
alter table experiment_material add column catalog_material_id varchar(64);
alter table experiment_material add column catalog_specification varchar(300);
alter table experiment_material add column catalog_unit varchar(20);
