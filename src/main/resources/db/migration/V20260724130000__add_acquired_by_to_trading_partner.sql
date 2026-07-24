ALTER TABLE common_company.common_trading_partner
    ADD COLUMN IF NOT EXISTS acquired_by_id UUID;

COMMENT ON COLUMN common_company.common_trading_partner.acquired_by_id IS
    'Immutable user id that initiated the customer relationship; null for supplier-only or unknown legacy acquisition.';

UPDATE common_company.common_trading_partner
SET acquired_by_id = created_by
WHERE partner_type IN ('CUSTOMER', 'BOTH')
  AND acquired_by_id IS NULL
  AND created_by IS NOT NULL;
