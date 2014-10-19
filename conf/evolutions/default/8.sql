# --- !Ups
ALTER TABLE users add column roles varchar(255) not null default 'jury';

# --- !Downs
ALTER TABLE users drop column roles;
