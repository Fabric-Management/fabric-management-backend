-- =====================================================
-- UPDATE: Make prod_fiber_iso_code truly platform-level
-- =====================================================
-- Set tenant_id = SYSTEM_TENANT_ID for all reference data
-- Users can CREATE new fiber types but cannot UPDATE/DELETE

-- Ensure all reference data has SYSTEM_TENANT_ID
UPDATE production.prod_fiber_iso_code 
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id != '00000000-0000-0000-0000-000000000000';

UPDATE production.prod_fiber_category 
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id != '00000000-0000-0000-0000-000000000000';

UPDATE production.prod_fiber_attribute 
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id != '00000000-0000-0000-0000-000000000000';

UPDATE production.prod_fiber_certification 
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id != '00000000-0000-0000-0000-000000000000';

-- Add comment about CREATE-ONLY policy
COMMENT ON COLUMN production.prod_fiber_iso_code.tenant_id IS 
'Always SYSTEM_TENANT_ID. Tenants can CREATE new fiber types but cannot UPDATE/DELETE. Platform admins manage all entries.';

