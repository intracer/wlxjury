# --- !Ups

CREATE TABLE wlxjury.monument (
  id varchar(255),
  name varchar(400) not null,
  description varchar(4000),
  place varchar(4000),
  photo varchar(400),
  gallery varchar(400),
  page varchar(400)
);