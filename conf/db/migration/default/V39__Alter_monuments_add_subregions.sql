alter table monument add column adm1 varchar(6);

update monument set adm1 = substr(id, 1, 6);

CREATE INDEX adm1_index ON monument(adm1);