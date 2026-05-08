-- =============================================================================
-- Phase 6/b: Supplier Quote Status Enrichment
-- Adds UNDER_REVIEW status constraint and partial composite indexes
-- =============================================================================

-- 1. Add UNDER_REVIEW status support via CHECK constraint
ALTER TABLE procurement.supplier_quote
  ADD CONSTRAINT ck_sq_status
  CHECK (status IN ('RECEIVED','UNDER_REVIEW','ACCEPTED','REJECTED','EXPIRED'));

-- 2. Partial composite indexes for performance
CREATE INDEX IF NOT EXISTS idx_sq_tenant_status_active
  ON procurement.supplier_quote (tenant_id, status) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_sq_tenant_valid_until_active
  ON procurement.supplier_quote (tenant_id, valid_until) WHERE is_active = true AND status IN ('RECEIVED','UNDER_REVIEW');
