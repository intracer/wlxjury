# --- !Ups
CREATE TABLE "rounds"
(
    "id" BIGSERIAL NOT NULL,
    "name" VARCHAR,
    "number" integer NOT NULL,
    "created_at" TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    "contest" INT DEFAULT 14
);
