# --- !Ups
create table images (
  page_id bigint not null,
  contest bigint not null,
  title varchar(4000),
  url varchar(4000),
  page_url varchar(4000),
  last_round int,
  width int,
  height int
);

alter table images add CONSTRAINT imagest_pkey PRIMARY KEY (page_id);

# --- alter table images add column width int;
# --- alter table images add height int;
# --- alter table images rename column pageid to page_id;

