create table contest_user (
                              user_id bigint unsigned NOT NULL,
                              contest_id bigint unsigned NOT NULL,
                              role varchar(255),
                              active BOOLEAN
);

alter table contest_user
    add CONSTRAINT FK_contest_user_user_id FOREIGN KEY (user_id) REFERENCES users(id) on DELETE CASCADE;

alter table contest_user
    add CONSTRAINT FK_contest_user_contest_id FOREIGN KEY (contest_id) REFERENCES contest_jury(id) on DELETE CASCADE;

create UNIQUE INDEX cu_contest_user on contest_user(user_id, contest_id);

insert into contest_user(user_id, contest_id, role, active)
select id as user_id, contest_id, roles as role, true from users
where contest_id is not null;

-- alter table users drop column contest_id;