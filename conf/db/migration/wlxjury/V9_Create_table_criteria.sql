CREATE TABLE criteria (
  id SERIAL not null,
  round INTEGER,
  name varchar(255) not null,
  contest int(11) DEFAULT NULL
);

alter table selection add column criteria_id integer DEFAULT null;
