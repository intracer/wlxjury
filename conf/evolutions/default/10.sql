# --- !Ups
CREATE TABLE wlxjury.rounds
(
    id SERIAL NOT NULL,
    name VARCHAR(255),
    number integer NOT NULL,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    contest INT DEFAULT 14
);
