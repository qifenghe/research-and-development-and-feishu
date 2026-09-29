create table material_classification (
 id varchar(64) primary key,
 parent_id varchar(64) references material_classification(id),
 parent_scope varchar(64) not null,
 name varchar(100) not null,
 name_key varchar(100) not null,
 business_type varchar(30) not null,
 sort_order integer not null default 0,
 status varchar(20) not null default 'ACTIVE',
 version integer not null default 0,
 created_at timestamp not null default current_timestamp,
 updated_at timestamp not null default current_timestamp,
 unique(parent_scope,name_key)
);
insert into material_classification(id,parent_scope,name,name_key,business_type,sort_order) values
 ('CAT-RAW','ROOT','原料','原料','RAW',10),
 ('CAT-AUXILIARY','ROOT','辅料','辅料','AUXILIARY',20),
 ('CAT-PACKAGING','ROOT','包装材料','包装材料','PACKAGING',30),
 ('CAT-PROCESS_WATER','ROOT','工艺用水','工艺用水','PROCESS_WATER',40);
insert into material_classification(id,parent_id,parent_scope,name,name_key,business_type,sort_order) values
 ('CAT-PORK','CAT-RAW','CAT-RAW','猪','猪','RAW',10),
 ('CAT-BEEF','CAT-RAW','CAT-RAW','牛','牛','RAW',20),
 ('CAT-LAMB','CAT-RAW','CAT-RAW','羊','羊','RAW',30),
 ('CAT-POULTRY','CAT-RAW','CAT-RAW','禽类','禽类','RAW',40),
 ('CAT-AQUATIC','CAT-RAW','CAT-RAW','水产','水产','RAW',50),
 ('CAT-VEGETABLE','CAT-RAW','CAT-RAW','蔬菜','蔬菜','RAW',60),
 ('CAT-SOLID','CAT-AUXILIARY','CAT-AUXILIARY','固体辅料','固体辅料','AUXILIARY',10),
 ('CAT-SEMISOLID','CAT-AUXILIARY','CAT-AUXILIARY','半固体辅料','半固体辅料','AUXILIARY',20),
 ('CAT-LIQUID','CAT-AUXILIARY','CAT-AUXILIARY','液体辅料','液体辅料','AUXILIARY',30);
alter table material_catalog add column classification_id varchar(64) references material_classification(id);
update material_catalog set classification_id=concat('CAT-',category);
create index idx_material_classification on material_catalog(classification_id);
insert into role_permission(id,role_code,http_method,path_pattern,enabled,description,sort_order,updated_at)
select concat('MC32-',role_code),role_code,'GET','/api/v1/material-classifications',true,'查询物料分类',214,current_timestamp
from (select distinct role_code from role_permission where role_code in ('RND_ENGINEER','RND_DIRECTOR','RND_ASSISTANT')) r;
