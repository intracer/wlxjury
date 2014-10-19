# --- !Ups

alter TABLE selection add column round integer;
update selection set round = 1;

# --- !Downs

alter TABLE selection drop column round;
