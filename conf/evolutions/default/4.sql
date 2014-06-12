# --- !Ups

alter TABLE wlxjury.selection add column email varchar(255);

# --- !Downs

alter TABLE wlxjury.selection drop column email;
