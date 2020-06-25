CREATE TABLE monument (
  id varchar(190),
  name varchar(400) not null,
  description varchar(4000),
  place text,
  photo varchar(400),
  gallery varchar(400),
  page varchar(400),
  typ varchar(255),
  sub_type varchar(255),
  user varchar(400) DEFAULT NULL,
  area varchar(400) DEFAULT NULL,
  resolution varchar(400) DEFAULT NULL,
  lat varchar(32) DEFAULT NULL,
  lon varchar(32) DEFAULT NULL,
  `year` varchar(255),
  city varchar(255),
  contest bigint
);