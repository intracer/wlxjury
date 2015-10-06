CREATE TABLE selection (
	id SERIAL not null PRIMARY KEY,
  jury_id integer not null,
  created_at timestamp not null,
  deleted_at timestamp null,
  round integer,
  page_id bigint,
  rate int
);