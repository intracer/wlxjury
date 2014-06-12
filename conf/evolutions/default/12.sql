# --- !Ups
CREATE TABLE wlxjury.contest
(
    id SERIAL NOT NULL,
    country VARCHAR(255) NOT NULL,
    year integer NOT NULL,
    images VARCHAR(4000)
);

alter table wlxjury.contest add column name varchar not null default 'Wiki Loves Earth';

alter table wlxjury.contest add CONSTRAINT contest_pkey PRIMARY KEY (id);

INSERT INTO contest(country, year, name) values ('Andorra & Catalan areas', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Armenia & Nagorno-Karabakh', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Austria', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Azerbaijan', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Brazil', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Germany', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Estonia', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Ghana', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('India', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Macedonia', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Nepal', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Netherlands', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Serbia', 2014, 'Wiki Loves Earth');
INSERT INTO contest(country, year, name) values ('Ukraine', 2014, 'Wiki Loves Earth');

#--drop table contest;
