create table images (
  page_id bigint PRIMARY KEY,
  contest bigint not null,
  title varchar(4000),
  url varchar(4000),
  page_url varchar(4000),
  last_round int,
  width int,
  height int,
  monument_id varchar(190)
);
