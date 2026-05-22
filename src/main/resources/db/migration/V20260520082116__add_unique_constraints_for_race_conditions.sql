DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_batch_tenant_code'
    ) THEN
        ALTER TABLE production.production_execution_batch ADD CONSTRAINT uq_batch_tenant_code UNIQUE (tenant_id, batch_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_wo_output_tenant_su'
    ) THEN
        ALTER TABLE production.work_order_output ADD CONSTRAINT uq_wo_output_tenant_su UNIQUE (tenant_id, stock_unit_id);
    END IF;
END $$;
