alter table monument add column adm0 varchar(3);

update monument set adm0 = substr(id, 1, 2);

CREATE INDEX adm0_index ON monument(adm0);