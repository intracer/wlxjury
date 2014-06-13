# --- !Ups

CREATE TABLE wlxjury.selection (
	id SERIAL not null,
  jury_id integer not null,
  created_at timestamp not null,
  deleted_at timestamp null
);

# --- !Downs

DROP TABLE wlxjury.selection;