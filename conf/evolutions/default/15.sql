# --- !Ups

alter table rounds add COLUMN previous integer;

alter table rounds add column prev_selected_by integer;