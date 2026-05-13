-- Cross-schema FK constraints dropped.
-- Referential integrity enforced at application layer via FiberCertificationQueryService.
ALTER TABLE common_company.partner_trading_partner_certification
    DROP CONSTRAINT IF EXISTS fk_ptpc_certification;

ALTER TABLE common_company.organization_certification
    DROP CONSTRAINT IF EXISTS fk_oc_certification;
