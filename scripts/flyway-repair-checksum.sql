-- Fix Flyway checksum mismatch when a migration file was changed after it was applied.
-- Use when you see: "Migration checksum mismatch for migration version 20250314190000"
--
-- Run: make db-repair-checksum
-- Or:  PGPASSWORD=local_dev_2026 psql -h localhost -p 5432 -U fabric_user -d fabric_management -f scripts/flyway-repair-checksum.sql
-- Or run the UPDATE below in any PostgreSQL client connected to fabric_management.

UPDATE flyway_schema_history
SET checksum = 1523355938
WHERE version = '20250314190000';
