alter table experiment_form alter column finished_output_quantity type numeric(14,4);
alter table experiment_form drop constraint ck_experiment_form_finished_output_unit_allowed;
alter table experiment_form add constraint ck_experiment_form_finished_output_unit_allowed
    check (finished_output_unit in ('袋','盒','份','个','盘','kg'));
