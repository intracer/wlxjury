# --- !Ups

alter TABLE wlxjury.selection add column round integer;
update selection set round = 1;

# --- !Downs

alter TABLE wlxjury.selection drop column round;
