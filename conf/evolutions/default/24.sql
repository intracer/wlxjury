# --- !Ups

alter table monument add column user varchar(400) DEFAULT NULL;
alter table monument add column area varchar(400) DEFAULT NULL;
alter table monument add column resolution varchar(400) DEFAULT NULL;
alter table monument add column lat varchar(32) DEFAULT NULL;
alter table monument add column lon varchar(32) DEFAULT NULL;

