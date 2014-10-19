# --- !Ups
ALTER TABLE users add column password varchar(255) not null;

# --- !Downs

ALTER TABLE users DROP column password;

