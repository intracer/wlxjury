# --- !Ups

alter table wlxjury.selection drop column round;
alter table wlxjury.selection drop column jury_id;

alter table wlxjury.selection add column round int;
alter table wlxjury.selection add column jury_id int;