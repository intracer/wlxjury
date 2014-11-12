# --- !Ups

CREATE TABLE comment (
  id SERIAL not null,
  user_id integer not null,
  username varchar(255) not null,
  round integer not null,
  at timestamp null,
  body text not null
);