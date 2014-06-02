# --- !Ups

alter table rounds add CONSTRAINT round_pkey PRIMARY KEY (id);
alter table users add CONSTRAINT user_pkey PRIMARY KEY (id);
#---CREATE TABLE round_jury (
#---#    round_id INTEGER REFERENCES rounds (id) ON UPDATE CASCADE ON DELETE CASCADE
#---#  , jury_id  INTEGER REFERENCES users (id) ON UPDATE CASCADE
#---#  , CONSTRAINT round_jury_pkey PRIMARY KEY (round_id, jury_id)  -- explicit pk
#---#);

ALTER TABLE rounds add column roles varchar not null default 'jury';
ALTER TABLE rounds add column rates integer default 1;
ALTER TABLE rounds add column limit_min integer default 1;
ALTER TABLE rounds add column limit_max integer default 50;
ALTER TABLE rounds add column recommended integer default null;