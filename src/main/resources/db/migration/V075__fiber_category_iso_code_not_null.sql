-- ============================================================================
-- V075: Fiber Category and ISO Code NOT NULL
-- ============================================================================
-- Makes fiber_category_id and fiber_iso_code_id required on prod_fiber.
-- Ensures all fibers have a category and ISO code for proper classification.
-- ============================================================================

ALTER TABLE production.prod_fiber
  ALTER COLUMN fiber_category_id SET NOT NULL;

ALTER TABLE production.prod_fiber
  ALTER COLUMN fiber_iso_code_id SET NOT NULL;
