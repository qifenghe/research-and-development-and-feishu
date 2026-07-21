alter table experiment_form
    add column yield_calculation_mode varchar(40) not null default 'SELECTED_PRIMARY_MATERIALS';
