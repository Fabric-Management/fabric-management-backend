-- Uniqueness: one active batch certification per (batch, certification, scope);
-- for SUPPLIER scope partner_certification_id is part of the key;
-- for FACILITY scope org_certification_id is part of the key.
-- COALESCE makes NULLs comparable so BATCH scope (both null) gets a single slot per cert.
CREATE UNIQUE INDEX uq_bc_batch_cert_scope_partner_org_active
ON production.production_execution_batch_certification (
    batch_id,
    certification_id,
    scope,
    COALESCE(partner_certification_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(org_certification_id, '00000000-0000-0000-0000-000000000000'::uuid)
)
WHERE is_active = true;

COMMENT ON INDEX production.uq_bc_batch_cert_scope_partner_org_active IS
'Ensures at most one active batch certification per (batch, certification, scope, partner_cert, org_cert). SUPPLIER: partner_certification_id in key; FACILITY: org_certification_id in key.';
