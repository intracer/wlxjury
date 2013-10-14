# --- !Ups

CREATE TABLE "user" (
	id SERIAL,
	name varchar,
	login varchar
);

# --- !Downs

DROP TABLE "user";
