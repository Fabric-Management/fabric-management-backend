DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        REVOKE DELETE ON TABLE sales.customer_commercial_assignment FROM fabric_app;
    END IF;
END $$;
