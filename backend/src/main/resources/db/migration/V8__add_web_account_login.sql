alter table user_account add column username varchar(100);
alter table user_account add column password_hash varchar(300);
alter table user_account add column last_login_at timestamp;
alter table user_account alter column feishu_user_id drop not null;

create unique index uk_user_account_username on user_account(username);
