# --- !Ups

alter TABLE "selection" add column email varchar;

# --- !Downs

alter TABLE "selection" drop column email;
