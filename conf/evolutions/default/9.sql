# --- !Ups
ALTER TABLE users add column contest integer default 14;

# --- !Downs
ALTER TABLE users drop column contest;
