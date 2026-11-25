-- Runs once, the first time the Postgres container starts with an EMPTY volume.
-- One Postgres server, three separate databases: each service owns exactly one and
-- nobody can join across them. (On Azure in Session 6 these can be separate servers.)
-- Changed this file? Run "docker compose down -v" so it runs again.
CREATE DATABASE users_db;
CREATE DATABASE catalog_db;
CREATE DATABASE orders_db;
