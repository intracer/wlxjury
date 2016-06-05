# --- !Ups

alter table contest_jury add COLUMN greeting TEXT;
alter table contest_jury add COLUMN use_greeting BOOLEAN;