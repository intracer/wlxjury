# --- !Ups

alter table selection drop column email;
alter table selection drop column filename;

alter table selection drop column round;
alter table selection drop column juryid;

alter table selection add column page_id bigint;
alter table selection add column round bigint;
alter table selection add column juryid bigint;