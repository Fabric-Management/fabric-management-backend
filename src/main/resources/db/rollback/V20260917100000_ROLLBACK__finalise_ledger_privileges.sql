-- Restore only the two privileges removed by DB-PRIV-3.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT UPDATE ON TABLE sales.customer_commercial_assignment TO fabric_system;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT DELETE ON TABLE sales_ord.order_cover_evidence_stream TO fabric_app;
    END IF;
END $$;
