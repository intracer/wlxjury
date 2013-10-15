# --- !Ups

CREATE TABLE "user" (
	id BIGSERIAL not null,
	name varchar not null,
	login varchar not null
);

# --- !Downs

DROP TABLE "user";
