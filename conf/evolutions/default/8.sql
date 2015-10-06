# --- !Ups

CREATE TABLE comment (
  id SERIAL not null,
  user_id integer not null,
  username varchar(255) not null,
  round integer not null,
  created_at varchar(40),
  body text not null,
  room integer not null default 1
);

# --- !Downs
DROP TABLE comment;