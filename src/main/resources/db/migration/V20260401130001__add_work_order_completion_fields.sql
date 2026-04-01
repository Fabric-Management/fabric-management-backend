-- Sprint 4: Add WorkOrder completion fields

ALTER TABLE production.prod_work_order
    ADD COLUMN IF NOT EXISTS actual_qty NUMERIC(15,3),
    ADD COLUMN IF NOT EXISTS yield_percentage NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_by UUID;
