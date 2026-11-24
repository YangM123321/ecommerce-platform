-- One schema per module. Modules must never query another module's schema.
-- In Session 4 each schema becomes its own service's database.
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS ordering;
