ALTER TABLE procurement.purchase_order
  ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'GENERIC',
  ADD COLUMN module_specs JSONB NOT NULL DEFAULT '{"specType":"GENERIC"}';

ALTER TABLE procurement.purchase_order_line
  ADD COLUMN module_specs JSONB NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_po_tenant_module_type
    ON procurement.purchase_order(tenant_id, module_type) WHERE is_active = true;

ALTER TABLE procurement.purchase_order
    ADD CONSTRAINT ck_po_module_type
    CHECK (module_type IN ('FIBER','YARN','FABRIC','DYE_FINISHING','GENERIC'));
