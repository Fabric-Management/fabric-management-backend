ALTER TABLE common_company.common_trading_partner
    ADD COLUMN IF NOT EXISTS customer_relationship_established_at TIMESTAMPTZ;

COMMENT ON COLUMN
    common_company.common_trading_partner.customer_relationship_established_at IS
    'Immutable known customer-relationship establishment timestamp; null for legacy rows with unknown history.';

CREATE OR REPLACE FUNCTION common_company.guard_customer_relationship_established_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.customer_relationship_established_at IS NOT NULL
       AND NEW.customer_relationship_established_at
           IS DISTINCT FROM OLD.customer_relationship_established_at THEN
        RAISE EXCEPTION
            'customer_relationship_established_at is immutable once recorded'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_customer_relationship_established_at_immutable
    BEFORE UPDATE ON common_company.common_trading_partner
    FOR EACH ROW
    EXECUTE FUNCTION common_company.guard_customer_relationship_established_at();
