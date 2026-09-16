DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT DELETE ON TABLE sales.customer_commercial_assignment TO fabric_app;
    END IF;
END $$;
