-- ============================================================================
-- V20260611050100: Consolidate prod_fiber + prod_product to TEMPLATE tenant
-- ============================================================================
-- The fiber seed migration (R__001) originally created 52 pure fibers and
-- their corresponding products under the legacy "system" tenant
-- (00000000-0000-0000-0000-000000000000). This is incorrect — these are
-- canonical platform fibers owned by the TEMPLATE tenant.
--
-- This migration retags them so RLS carve-out can make them visible to all
-- tenants without copying (PF4 compliance).
-- ============================================================================

UPDATE production.prod_fiber
SET    tenant_id  = '00000000-0000-0000-ffff-000000000001',
       updated_at = now()
WHERE  tenant_id  = '00000000-0000-0000-0000-000000000000';

UPDATE production.prod_product
SET    tenant_id  = '00000000-0000-0000-ffff-000000000001',
       updated_at = now()
WHERE  tenant_id  = '00000000-0000-0000-0000-000000000000';
