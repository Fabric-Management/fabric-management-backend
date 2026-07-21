CREATE OR REPLACE FUNCTION production.reject_quality_decision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '55000';
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        REVOKE UPDATE, DELETE ON TABLE
            production.quality_decision,
            production.quality_decision_unit
        FROM fabric_system;
    END IF;
END $$;
