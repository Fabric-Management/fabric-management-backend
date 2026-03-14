-- Reset fabric_management database for local development.
-- Run: make db-reset-local  (or: psql -U postgres -d postgres -f scripts/db-reset-local.sql)
-- Then: make run

\connect postgres;

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'fabric_management' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS fabric_management;

CREATE DATABASE fabric_management
  OWNER fabric_user
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8'
  TEMPLATE template0;

\echo 'Database fabric_management has been reset. Run: make run'
