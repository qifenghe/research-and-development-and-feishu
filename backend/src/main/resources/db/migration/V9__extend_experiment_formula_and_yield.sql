alter table experiment_material add column material_category varchar(30);
alter table experiment_material add column is_primary_material boolean not null default false;
alter table experiment_material add column formula_ratio numeric(10, 6);
alter table experiment_material add column input_unit varchar(10) not null default 'kg';

update experiment_material
set material_category = case
    when upper(stage) in ('AUXILIARY', 'AUX') or stage like '%辅%' then 'AUXILIARY'
    when upper(stage) in ('PACKAGING', 'PACKAGE') or stage like '%包%' then 'PACKAGING'
    else 'RAW'
end;

alter table experiment_process add column remaining_weight_kg numeric(14, 4);
alter table experiment_process add column remaining_disposition varchar(30);
alter table experiment_process add column loss_weight_kg numeric(14, 4);

alter table experiment_form add column finished_output_weight_kg numeric(14, 4);
alter table experiment_form add column finished_yield_ratio numeric(10, 6);
