CREATE TABLE rounds
(
    id SERIAL NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    number integer NOT NULL,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP null,
    contest INT,
    roles varchar(255) not null default 'jury',
    rates integer default 1,
    limit_min integer default 1,
    limit_max integer default 50,
    recommended integer default null,
    distribution int default 0,
    active BOOLEAN,
    optional_rate BOOLEAN,
    jury_org_view BOOLEAN
);
