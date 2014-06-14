# --- !Ups

alter table contest add column monument_id_template varchar(128);

update contest set monument_id_template = 'WLE-AD-ES' where country = 'Andorra & Catalan areas';
update contest set monument_id_template = 'Natural Heritage Armenia & Nagorno-Karabakh' where country = 'Armenia & Nagorno-Karabakh';
update contest set monument_id_template = 'Naturdenkmal Österreich' where country = 'Austria';  -- "Nationalpark Österreich"
update contest set monument_id_template = 'Natural Heritage Azerbaijan' where country = 'Azerbaijan';
update contest set monument_id_template = 'Loodusmälestis' where country = 'Estonia';
update contest set monument_id_template = 'Ghanaian Natural Heritage' where country = 'Ghana';
update contest set monument_id_template = 'India' where country = 'India';
update contest set monument_id_template = 'Wiki Loves Earth Nepal' where country = 'Nepal';
update contest set monument_id_template = 'Wiki Loves Earth Netherlands' where country = 'Netherlands';
update contest set monument_id_template = 'UkrainianNaturalHeritageSite' where country = 'Ukraine';

update contest set monument_id_template = 'wle14mk' where country = 'Macedonia';

update contest set country='the Netherlands' where country = 'Netherlands';

-- update contest set monument_id_template = 'Brazil' where country = 'Brazil';
-- update contest set monument_id_template = 'Naturdenkmal' where country = 'Germany';
-- update contest set monument_id_template = 'Macedonia' where country = 'Serbia';

-- Wiki Loves Earth Algeria


