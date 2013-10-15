# --- !Ups

CREATE TABLE "selection" (
	id BIGSERIAL not null,
	filename varchar not null,
	fileid varchar not null,
  juryid integer not null,
  created_at timestamp not null,
  deleted_at timestamp
);

# --- !Downs

DROP TABLE "selection";