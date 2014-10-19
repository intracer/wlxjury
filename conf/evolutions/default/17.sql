# --- !Ups

alter table selection drop column round;
alter table selection drop column jury_id;

alter table selection add column round int;
alter table selection add column jury_id int;