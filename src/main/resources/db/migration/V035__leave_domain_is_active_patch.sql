-- ============================================================================
-- V035: Leave Domain is_active Patch
-- ----------------------------------------------------------------------------
-- Aligns leave domain tables with BaseEntity contract by ensuring the
-- is_active column exists everywhere (needed for schema validation).
-- Last Updated: 2025-11-09
-- ============================================================================

-- No-op: schema definitions now include BaseEntity columns.
