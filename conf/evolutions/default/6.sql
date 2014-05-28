# --- !Ups

DROP TABLE "users";

CREATE TABLE "users" (
	id BIGSERIAL not null,
	fullname varchar not null,
	email varchar not null,
  created_at timestamp not null,
  deleted_at timestamp
);


# --- !Downs


