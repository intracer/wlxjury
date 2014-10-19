# --- !Ups

CREATE TABLE monument (
  id varchar(255),
  name varchar(400) not null,
  description varchar(4000),
  place text,
  photo varchar(400),
  gallery varchar(400),
  page varchar(400),
  typ varchar(255),
  sub_type varchar(255)
);