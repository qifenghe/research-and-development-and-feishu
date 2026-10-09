-- Preserve classification IDs and material business types; only reorganize directories.
insert into material_classification(id,parent_scope,name,name_key,business_type,sort_order)
values ('CAT-PACKAGING-ROOT','ROOT','包材','包材','PACKAGING',30);

update material_classification
set parent_id='CAT-PACKAGING-ROOT',parent_scope='CAT-PACKAGING-ROOT',sort_order=10,
    version=version+1,updated_at=current_timestamp
where id='CAT-PACKAGING';

update material_classification
set parent_id='CAT-RAW',parent_scope='CAT-RAW',sort_order=90,
    version=version+1,updated_at=current_timestamp
where id='CAT-PROCESS_WATER';
