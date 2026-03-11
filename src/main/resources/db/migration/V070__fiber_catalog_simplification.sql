-- ============================================================================
-- V070: Fiber Catalog Simplification (Phase 1)
-- ============================================================================
-- Removes:
--   - prod_fiber.fiber_grade column
--   - prod_fiber_attribute_link table (FiberAttributeLink)
--   - prod_fiber_certification_link table (FiberCertificationLink)
-- ============================================================================

-- Drop junction tables first (they reference prod_fiber)
DROP TABLE IF EXISTS production.prod_fiber_attribute_link CASCADE;
DROP TABLE IF EXISTS production.prod_fiber_certification_link CASCADE;

-- Drop fiber_grade column from prod_fiber
ALTER TABLE production.prod_fiber
DROP COLUMN IF EXISTS fiber_grade;
