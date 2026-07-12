alter table rnd_task add column product_owner_name varchar(100);
alter table experiment_form add column finished_output_quantity integer;
alter table experiment_form add column finished_output_unit varchar(20) default '袋' not null;

update rnd_task set product_owner_name = assignee_name where product_owner_name is null;
