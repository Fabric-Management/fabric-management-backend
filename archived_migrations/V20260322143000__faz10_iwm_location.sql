-- =========================================================================
-- MODULE: IWM (Faz 10.1) - Lokasyon Yönetimi Birleştirme & Taşıma
-- =========================================================================

-- 1. SCHEMA DEFINITION
CREATE SCHEMA IF NOT EXISTS iwm;

-- 2. TABLE RELOCATION & RENAMING
-- Move from `production` schema to `iwm` schema
ALTER TABLE production.production_execution_warehouse_location SET SCHEMA iwm;

-- Rename table to `warehouse_location`
ALTER TABLE iwm.production_execution_warehouse_location RENAME TO warehouse_location;

-- Note: PostgreSQL automatically updates foreign keys referencing this table.
-- Existing indexes, constraints are kept.
