create table sample_request (
    id varchar(32) primary key,
    sample_no varchar(32) not null unique,
    product_name varchar(200) not null,
    product_type varchar(100) not null,
    customer_name varchar(200) not null,
    specification varchar(100) not null,
    creator_name varchar(100) not null,
    status varchar(50) not null,
    created_at timestamp not null
);

create table sample_project (
    id varchar(32) primary key,
    request_id varchar(32),
    sample_no varchar(32) not null unique,
    product_name varchar(200) not null,
    product_type varchar(100) not null,
    customer_name varchar(200) not null,
    specification varchar(100) not null,
    status varchar(50) not null,
    created_at timestamp not null,
    constraint fk_sample_project_request foreign key (request_id) references sample_request(id)
);

create table sample_version (
    id varchar(32) primary key,
    project_id varchar(32) not null,
    sample_no varchar(32) not null,
    product_name varchar(200) not null,
    product_type varchar(100) not null,
    specification varchar(100) not null,
    version_no varchar(20) not null,
    version_number integer not null,
    version_code varchar(20) not null,
    owner_name varchar(100),
    author_name varchar(100),
    effective_date date,
    reference_output_kg numeric(14, 4),
    unit_weight_kg numeric(14, 4),
    created_at timestamp not null,
    constraint fk_sample_version_project foreign key (project_id) references sample_project(id),
    constraint uk_sample_version_project_version unique (project_id, version_code)
);

create table rnd_task (
    id varchar(32) primary key,
    project_id varchar(32) not null,
    version_id varchar(32) not null,
    sample_no varchar(32) not null,
    product_name varchar(200) not null,
    version_code varchar(20) not null,
    status varchar(50) not null,
    assignee_name varchar(100),
    due_date date,
    created_at timestamp not null,
    assigned_at timestamp,
    accepted_at timestamp,
    constraint fk_rnd_task_project foreign key (project_id) references sample_project(id),
    constraint fk_rnd_task_version foreign key (version_id) references sample_version(id)
);

create table experiment_form (
    id varchar(32) primary key,
    task_id varchar(32) not null,
    project_id varchar(32) not null,
    version_id varchar(32) not null,
    sample_no varchar(32) not null,
    product_name varchar(200) not null,
    version_code varchar(20) not null,
    status varchar(50) not null,
    operator_name varchar(100) not null,
    summary varchar(1000),
    saved_at timestamp not null,
    submitted_at timestamp,
    constraint fk_experiment_form_task foreign key (task_id) references rnd_task(id),
    constraint fk_experiment_form_project foreign key (project_id) references sample_project(id),
    constraint fk_experiment_form_version foreign key (version_id) references sample_version(id)
);

create table experiment_material (
    id varchar(32) primary key,
    experiment_form_id varchar(32) not null,
    stage varchar(100),
    sequence integer not null,
    material_code varchar(100),
    material_name varchar(200) not null,
    weight_kg numeric(14, 4) not null,
    utilization_rate numeric(10, 6) not null,
    remark varchar(500),
    constraint fk_experiment_material_form foreign key (experiment_form_id) references experiment_form(id)
);

create table test_assignment (
    id varchar(32) primary key,
    experiment_form_id varchar(32) not null,
    task_id varchar(32) not null,
    version_id varchar(32) not null,
    tester_name varchar(100) not null,
    status varchar(50) not null,
    assigned_at timestamp not null,
    constraint fk_test_assignment_form foreign key (experiment_form_id) references experiment_form(id),
    constraint fk_test_assignment_task foreign key (task_id) references rnd_task(id),
    constraint fk_test_assignment_version foreign key (version_id) references sample_version(id)
);

create table test_record (
    id varchar(32) primary key,
    test_assignment_id varchar(32) not null,
    experiment_form_id varchar(32) not null,
    tester_name varchar(100) not null,
    result varchar(50) not null,
    comment varchar(1000),
    tested_at timestamp not null,
    constraint fk_test_record_assignment foreign key (test_assignment_id) references test_assignment(id),
    constraint fk_test_record_form foreign key (experiment_form_id) references experiment_form(id)
);

create table shipment_record (
    id varchar(32) primary key,
    version_id varchar(32) not null,
    sample_no varchar(32) not null,
    product_name varchar(200) not null,
    version_code varchar(20) not null,
    quantity integer not null,
    receiver_name varchar(100) not null,
    tracking_no varchar(100) not null,
    remark varchar(500),
    status varchar(50) not null,
    shipped_at timestamp not null,
    constraint fk_shipment_record_version foreign key (version_id) references sample_version(id)
);

create table customer_feedback (
    id varchar(32) primary key,
    shipment_id varchar(32) not null,
    feedback_by varchar(100) not null,
    result varchar(50) not null,
    comment varchar(1000),
    feedback_at timestamp not null,
    constraint fk_customer_feedback_shipment foreign key (shipment_id) references shipment_record(id)
);

create table pricing_file (
    id varchar(32) primary key,
    version_id varchar(32) not null,
    sample_no varchar(32) not null,
    product_name varchar(200) not null,
    version_code varchar(20) not null,
    pricing_version varchar(50) not null,
    file_name varchar(300) not null,
    status varchar(50) not null,
    content_length bigint not null,
    generated_at timestamp not null,
    constraint fk_pricing_file_version foreign key (version_id) references sample_version(id),
    constraint uk_pricing_file_version_pricing_version unique (version_id, pricing_version)
);

create table finance_notification (
    id varchar(32) primary key,
    pricing_file_id varchar(32) not null,
    recipient_name varchar(100) not null,
    remark varchar(500),
    status varchar(50) not null,
    notified_at timestamp not null,
    constraint fk_finance_notification_pricing_file foreign key (pricing_file_id) references pricing_file(id)
);

create table user_account (
    id varchar(32) primary key,
    name varchar(100) not null,
    feishu_user_id varchar(100) not null unique,
    role varchar(100) not null,
    department_name varchar(100),
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table feishu_notification (
    id varchar(32) primary key,
    business_type varchar(50) not null,
    business_id varchar(32) not null,
    recipient_user_id varchar(32) not null,
    recipient_feishu_user_id varchar(100) not null,
    template_key varchar(100) not null,
    title varchar(200) not null,
    content varchar(1000),
    status varchar(50) not null,
    send_attempts integer not null default 0,
    last_error varchar(500),
    created_at timestamp not null,
    sent_at timestamp,
    constraint fk_feishu_notification_user foreign key (recipient_user_id) references user_account(id)
);

create table archive_file (
    id varchar(32) primary key,
    business_type varchar(50) not null,
    business_id varchar(32) not null,
    version_id varchar(32),
    file_name varchar(300) not null,
    file_path varchar(500) not null,
    file_url varchar(500),
    category varchar(50),
    uploaded_by varchar(100),
    remark varchar(500),
    content_type varchar(100),
    file_size bigint,
    file_status varchar(50) not null,
    archived_at timestamp not null,
    constraint fk_archive_file_version foreign key (version_id) references sample_version(id)
);

create table audit_log (
    id varchar(32) primary key,
    business_type varchar(50) not null,
    business_id varchar(32) not null,
    action varchar(100) not null,
    operator_name varchar(100) not null,
    detail varchar(1000),
    created_at timestamp not null
);

create index idx_sample_request_name_time on sample_request(product_name, created_at);
create index idx_sample_project_name_time on sample_project(product_name, created_at);
create index idx_rnd_task_assignee_status on rnd_task(assignee_name, status);
create index idx_experiment_form_version_status on experiment_form(version_id, status);
create index idx_shipment_record_version_status on shipment_record(version_id, status);
create index idx_pricing_file_version_status on pricing_file(version_id, status);
create index idx_archive_file_business on archive_file(business_type, business_id);
create index idx_user_account_name_status on user_account(name, status);
create index idx_feishu_notification_status on feishu_notification(status, created_at);
create index idx_feishu_notification_business on feishu_notification(business_type, business_id);
