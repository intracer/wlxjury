# --- !Ups

alter table images add COLUMN description text;

# --- !Downs
alter table images drop column description;