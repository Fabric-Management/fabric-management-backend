-- DB-PRIV-3: close the two audited surplus privileges; preserve system purge DELETE.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        REVOKE UPDATE ON TABLE sales.customer_commercial_assignment FROM fabric_system;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        REVOKE DELETE ON TABLE sales_ord.order_cover_evidence_stream FROM fabric_app;
    END IF;
END $$;
