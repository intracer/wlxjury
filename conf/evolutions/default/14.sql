# --- !Ups

CREATE TABLE criteria_rate
(
  id BIGINT(20) UNSIGNED NOT NULL,
  selection INT(11) NOT NULL,
  criteria INT(11) NOT NULL,
  rate INT(11) NOT NULL
);
CREATE INDEX criteria_selection_index ON criteria_rate (selection);
CREATE UNIQUE INDEX id ON criteria_rate (id);