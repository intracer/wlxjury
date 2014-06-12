# --- !Ups
ALTER TABLE wlxjury.users add column roles varchar(255) not null default 'jury';