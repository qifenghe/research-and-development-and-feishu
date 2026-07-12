update experiment_form
set finished_yield_ratio = finished_yield_ratio * 100
where finished_yield_ratio is not null
  and finished_yield_ratio >= 0
  and finished_yield_ratio <= 1;

alter table experiment_form rename column finished_yield_ratio to finished_yield_percent;
