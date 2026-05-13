-- Allow multiple active batch certifications per (batch, cert, scope, partner, org) when
-- validity periods do not overlap. Overlap is enforced in application (BatchCertificationService).
DROP INDEX IF EXISTS production.uq_bc_batch_cert_scope_partner_org_active;
