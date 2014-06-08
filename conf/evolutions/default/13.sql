# --- !Ups
create table images (
  pageid bigint not null,
  contest int not null,
  title varchar,
  url varchar,
  page_url varchar,
  last_round int
);

alter table images add CONSTRAINT imagest_pkey PRIMARY KEY (pageid);