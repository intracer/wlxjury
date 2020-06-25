create table round_user (
                                  user_id bigint unsigned NOT NULL,
                                  round_id bigint unsigned NOT NULL,
                                  role varchar(255),
                                  active BOOLEAN
);

alter table round_user
    add CONSTRAINT FK_round_user_user_id FOREIGN KEY (user_id) REFERENCES users(id) on DELETE CASCADE;

alter table round_user
    add CONSTRAINT FK_round_user_round_id FOREIGN KEY (round_id) REFERENCES rounds(id) on DELETE CASCADE;

create UNIQUE INDEX ru_round_user on round_user(user_id, round_id);
