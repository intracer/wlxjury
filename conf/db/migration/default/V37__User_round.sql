create table user_round (
                                  user_id bigint unsigned NOT NULL,
                                  round_id bigint unsigned NOT NULL,
                                  role varchar(255),
                                  active BOOLEAN
);

alter table user_round
    add CONSTRAINT FK_user_round_user_id FOREIGN KEY (user_id) REFERENCES users(id) on DELETE CASCADE;

alter table user_round
    add CONSTRAINT FK_user_round_round_id FOREIGN KEY (round_id) REFERENCES rounds(id) on DELETE CASCADE;

create UNIQUE INDEX ur_user_round on user_round(user_id, round_id);
