alter table rnd_task add column product_owner_name varchar(100);
alter table experiment_form add column finished_output_quantity integer;
alter table experiment_form add column finished_output_unit varchar(20) default '袋' not null;
alter table experiment_form add constraint ck_experiment_form_finished_output_quantity_positive
    check (finished_output_quantity is null or finished_output_quantity > 0);
alter table experiment_form add constraint ck_experiment_form_finished_output_unit_allowed
    check (finished_output_unit in ('袋', '盒', '份', '个', '盘'));

update rnd_task set product_owner_name = assignee_name where product_owner_name is null;
