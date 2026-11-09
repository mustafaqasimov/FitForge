CREATE TABLE auth_rate_limits (
 scope varchar(128) PRIMARY KEY,
 window_start timestamptz NOT NULL,
 attempts integer NOT NULL
);
