-- One permanent stock ledger for quotes and sales orders. Do not discard overlapping owners.
ALTER TABLE production.stock_unit_soft_hold ALTER COLUMN quote_line_id DROP NOT NULL;
ALTER TABLE production.stock_unit_soft_hold ADD COLUMN owner_type varchar(255) NOT NULL DEFAULT 'QUOTE';
ALTER TABLE production.stock_unit_soft_hold ADD COLUMN owner_id uuid;
ALTER TABLE production.stock_unit_soft_hold ADD COLUMN owner_line_id uuid;
UPDATE production.stock_unit_soft_hold SET owner_line_id = quote_line_id;
ALTER TABLE production.stock_unit_soft_hold ALTER COLUMN owner_line_id SET NOT NULL;
ALTER TABLE production.stock_unit_soft_hold ADD CONSTRAINT uq_soft_hold_owner_piece UNIQUE (tenant_id, owner_line_id, stock_unit_id);
ALTER TABLE production.stock_unit_soft_hold ADD CONSTRAINT chk_soft_hold_owner CHECK (owner_type IN ('QUOTE', 'SALES_ORDER'));
-- Refuse the migration with concrete ownership evidence; never pick a winner or delete history.
DO $$
DECLARE conflicts text;
BEGIN
  SELECT string_agg(format('tenant=%s piece=%s owners=%s', tenant_id, stock_unit_id, owners), E'\n')
  INTO conflicts
  FROM (
    SELECT tenant_id, stock_unit_id, array_agg(owner_line_id ORDER BY owner_line_id) AS owners
    FROM production.stock_unit_soft_hold
    WHERE status = 'ACTIVE' AND is_active
    GROUP BY tenant_id, stock_unit_id HAVING count(*) > 1
    ORDER BY tenant_id, stock_unit_id LIMIT 20
  ) overlap;
  IF conflicts IS NOT NULL THEN
    RAISE EXCEPTION 'Conflicting active stock owners; reconcile these records before retrying migration: %', conflicts;
  END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_soft_hold_active_piece ON production.stock_unit_soft_hold(tenant_id, stock_unit_id)
  WHERE status = 'ACTIVE' AND is_active;

ALTER TABLE production.batch_lot_quantity_intent ALTER COLUMN quote_id DROP NOT NULL;
ALTER TABLE production.batch_lot_quantity_intent ALTER COLUMN quote_line_id DROP NOT NULL;
ALTER TABLE production.batch_lot_quantity_intent ADD COLUMN owner_type varchar(255) NOT NULL DEFAULT 'QUOTE';
ALTER TABLE production.batch_lot_quantity_intent ADD COLUMN owner_id uuid;
ALTER TABLE production.batch_lot_quantity_intent ADD COLUMN owner_line_id uuid;
ALTER TABLE production.batch_lot_quantity_intent ADD COLUMN owner_number varchar(50);
UPDATE production.batch_lot_quantity_intent SET owner_id = quote_id, owner_line_id = quote_line_id, owner_number = quote_number;
ALTER TABLE production.batch_lot_quantity_intent ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE production.batch_lot_quantity_intent ALTER COLUMN owner_line_id SET NOT NULL;
ALTER TABLE production.batch_lot_quantity_intent ADD CONSTRAINT uq_lot_intent_owner_batch UNIQUE (tenant_id, owner_line_id, batch_id);
ALTER TABLE production.batch_lot_quantity_intent ADD CONSTRAINT chk_lot_intent_owner CHECK (owner_type IN ('QUOTE', 'SALES_ORDER'));
CREATE INDEX IF NOT EXISTS idx_intent_owner_line ON production.batch_lot_quantity_intent(tenant_id, owner_line_id, status);
