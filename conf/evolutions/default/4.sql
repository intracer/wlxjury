# --- !Ups
CREATE TABLE contest_jury
(
    id SERIAL NOT NULL PRIMARY KEY,
    name varchar(255) not null default 'Wiki Loves Earth',
    country VARCHAR(255) NOT NULL,
    year integer NOT NULL,
    images VARCHAR(4000),
    current_round int default 0,
    monument_id_template varchar(128)
);

# --- !Downs
DROP TABLE contest_jury;
