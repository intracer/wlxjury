create table category (
  id SERIAL not null PRIMARY KEY,
  title VARCHAR(190) UNIQUE
);

create table category_members (
  category_id bigint unsigned NOT NULL,
  page_id bigint NOT NULL
);

alter table category_members
  add CONSTRAINT FK_category_member_category FOREIGN KEY (category_id) REFERENCES category(id) on DELETE CASCADE;

alter table category_members
  add CONSTRAINT FK_category_member_page_id FOREIGN KEY (page_id) REFERENCES images(page_id) on DELETE CASCADE;

create UNIQUE INDEX cm_category_page on category_members(category_id, page_id);

alter table contest_jury add COLUMN category_id bigint unsigned;

alter table images alter COLUMN contest SET DEFAULT 0;

alter table contest_jury
add CONSTRAINT FK_contest_category
FOREIGN KEY (category_id) REFERENCES category(id);

insert into category(title) select distinct CONCAT('Images from ', name, ' ', year,' in ', country) from contest_jury;

update contest_jury
set category_id = (SELECT id from
category where
  category.title =  CONCAT('Images from ', contest_jury.name, ' ', contest_jury.year,' in ', contest_jury.country));

INSERT into category_members
    select distinct category_id, page_id FROM
      contest_jury, images, category
where contest_jury.category_id = category.id
      and images.contest = contest_jury.id;