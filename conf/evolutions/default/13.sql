# --- !Ups
create table images (
  pageid bigint not null,
  contest bigint not null,
  title varchar,
  url varchar,
  page_url varchar,
  last_round int,
  width int,
  height int
);

alter table images add CONSTRAINT imagest_pkey PRIMARY KEY (pageid);

# --- alter table images add column width int;
# --- alter table images add height int;

