# --- !Ups
ALTER TABLE monument add column `year` varchar(255);
ALTER TABLE monument add column city varchar(255);
ALTER TABLE monument add column contest bigint default 14;