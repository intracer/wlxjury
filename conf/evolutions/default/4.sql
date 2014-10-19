# --- !Ups

alter TABLE selection add column email varchar(255);

# --- !Downs

alter TABLE selection drop column email;
