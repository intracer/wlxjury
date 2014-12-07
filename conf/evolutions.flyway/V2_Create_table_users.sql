CREATE TABLE users (
	id SERIAL not null PRIMARY KEY,
	fullname varchar(255) not null,
	email varchar(255) not null,
  created_at timestamp not null,
  deleted_at timestamp null,
  password varchar(255),
  roles varchar(255),
  contest integer,
  lang char(10)
);