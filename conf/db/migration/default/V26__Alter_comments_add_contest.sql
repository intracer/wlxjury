alter table comment add COLUMN contest_id bigint unsigned;

update comment c
  left join rounds r on c.round = r.id
set
  c.contest_id = r.contest;

alter table comment
  add CONSTRAINT FK_comment_contest FOREIGN KEY (contest_id) REFERENCES contest_jury(id);
