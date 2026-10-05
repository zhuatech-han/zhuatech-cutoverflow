-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
CREATE TABLE cutover_plan (
id bigint AUTO_INCREMENT PRIMARY KEY,
revision bigint NOT NULL,
code varchar(60) NOT NULL,
title varchar(160) NOT NULL,
category varchar(60) NOT NULL,
department_id bigint NOT NULL,
author_id bigint NOT NULL,
director_id bigint NOT NULL,
system_name varchar(160) NOT NULL,
scope_text varchar(2000) NOT NULL,
decision_criteria varchar(2000) NOT NULL,
recovery_criteria varchar(2000) NOT NULL,
status varchar(20) NOT NULL,
submitted boolean NOT NULL,
created_at timestamp(6) NOT NULL,
UNIQUE(code),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(author_id) REFERENCES account(id),
FOREIGN KEY(director_id) REFERENCES account(id)
);
CREATE TABLE cutover_step (
id bigint AUTO_INCREMENT PRIMARY KEY,
plan_id bigint NOT NULL,
title varchar(160) NOT NULL,
owner_id bigint NOT NULL,
reviewer_id bigint NOT NULL,
minutes int NOT NULL,
instructions varchar(2000) NOT NULL,
verification varchar(2000) NOT NULL,
rollback varchar(2000) NOT NULL,
FOREIGN KEY(plan_id) REFERENCES cutover_plan(id),
FOREIGN KEY(owner_id) REFERENCES account(id),
FOREIGN KEY(reviewer_id) REFERENCES account(id)
);
CREATE TABLE cutover_run (
id bigint AUTO_INCREMENT PRIMARY KEY,
revision bigint NOT NULL,
plan_id bigint NOT NULL,
department_id bigint NOT NULL,
mode varchar(20) NOT NULL,
reference_no varchar(100) NOT NULL,
status varchar(20) NOT NULL,
started_at timestamp(6) NOT NULL,
deadline timestamp(6) NOT NULL,
decision_note varchar(2000) NOT NULL,
FOREIGN KEY(plan_id) REFERENCES cutover_plan(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);
CREATE TABLE run_task (
id bigint AUTO_INCREMENT PRIMARY KEY,
run_id bigint NOT NULL,
step_id bigint NOT NULL,
status varchar(20) NOT NULL,
rollback_status varchar(20) NOT NULL,
execution_evidence varchar(2000) NOT NULL,
review_evidence varchar(2000) NOT NULL,
rollback_evidence varchar(2000) NOT NULL,
rollback_review varchar(2000) NOT NULL,
UNIQUE(run_id,step_id),
FOREIGN KEY(run_id) REFERENCES cutover_run(id),
FOREIGN KEY(step_id) REFERENCES cutover_step(id)
);
CREATE TABLE step_dependency (step_id bigint NOT NULL,predecessor_id bigint NOT NULL,PRIMARY KEY(step_id,predecessor_id),FOREIGN KEY(step_id) REFERENCES cutover_step(id),FOREIGN KEY(predecessor_id) REFERENCES cutover_step(id));
CREATE TABLE command_record (id bigint AUTO_INCREMENT PRIMARY KEY,request_key varchar(36) NOT NULL UNIQUE,fingerprint varchar(64) NOT NULL,result_id bigint NOT NULL);
CREATE TABLE flow_event (id bigint AUTO_INCREMENT PRIMARY KEY,kind varchar(20) NOT NULL,object_id bigint NOT NULL,department_id bigint NOT NULL,actor_id bigint NOT NULL,action varchar(40) NOT NULL,note varchar(2000) NOT NULL,created_at timestamp(6) NOT NULL,FOREIGN KEY(department_id) REFERENCES department(id),FOREIGN KEY(actor_id) REFERENCES account(id));
CREATE INDEX ix_plan_scope ON cutover_plan(department_id,status,created_at);
CREATE INDEX ix_run_plan ON cutover_run(plan_id,status);
CREATE INDEX ix_step_plan ON cutover_step(plan_id);
CREATE INDEX ix_flow_object ON flow_event(kind,object_id,created_at);
