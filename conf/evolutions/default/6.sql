# --- !Ups

CREATE TABLE users (
	id SERIAL not null,
	fullname varchar(255) not null,
	email varchar(255) not null,
  created_at timestamp not null,
  deleted_at timestamp null
);


# --- !Downs


