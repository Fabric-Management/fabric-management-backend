-- =========================================================================
-- MODULE: IWM (Faz 10 CR Patch) - Code Review Düzeltmeleri
-- CR-10-04: stock_adjustment_request → rejected_by, rejected_at alanları
-- CR-10-08: stock_count_assignee → BaseEntity alanları (tenant_id, uid, vb.)
-- CR-10-13: rma → rejected_by, rejected_at alanları
-- =========================================================================

-- CR-10-13: RMA rejected audit trail
ALTER TABLE iwm.rma ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE iwm.rma ADD COLUMN IF NOT EXISTS rejected_by UUID;

-- CR-10-04: StockAdjustmentRequest rejected audit trail
ALTER TABLE iwm.stock_adjustment_request ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE iwm.stock_adjustment_request ADD COLUMN IF NOT EXISTS rejected_by UUID;

-- CR-10-08: StockCountAssignee → BaseEntity alanları eklenmesi
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS uid VARCHAR(100);
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE iwm.stock_count_assignee ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- CR-10-16: FK Constraint örnekleri (referential integrity)
-- Not: Bu constraint'ler production ve iwm şemaları arası bağımlılık yaratır.
-- Mikroservis ayrımı yapılacaksa bu satırlar kaldırılabilir.
ALTER TABLE iwm.stock_reservation
  ADD CONSTRAINT IF NOT EXISTS fk_stock_res_location
  FOREIGN KEY (location_id) REFERENCES iwm.warehouse_location(id);

ALTER TABLE iwm.stock_count
  ADD CONSTRAINT IF NOT EXISTS fk_stock_count_location
  FOREIGN KEY (location_id) REFERENCES iwm.warehouse_location(id);

ALTER TABLE iwm.stock_transfer
  ADD CONSTRAINT IF NOT EXISTS fk_stock_transfer_from_loc
  FOREIGN KEY (from_location_id) REFERENCES iwm.warehouse_location(id);

ALTER TABLE iwm.stock_transfer
  ADD CONSTRAINT IF NOT EXISTS fk_stock_transfer_to_loc
  FOREIGN KEY (to_location_id) REFERENCES iwm.warehouse_location(id);

ALTER TABLE iwm.rma_line
  ADD CONSTRAINT IF NOT EXISTS fk_rma_line_rma
  FOREIGN KEY (rma_id) REFERENCES iwm.rma(id);

ALTER TABLE iwm.stock_count_line
  ADD CONSTRAINT IF NOT EXISTS fk_count_line_count
  FOREIGN KEY (stock_count_id) REFERENCES iwm.stock_count(id);

ALTER TABLE iwm.stock_count_assignee
  ADD CONSTRAINT IF NOT EXISTS fk_count_assignee_count
  FOREIGN KEY (stock_count_id) REFERENCES iwm.stock_count(id);

ALTER TABLE iwm.min_stock_rule
  ADD CONSTRAINT IF NOT EXISTS fk_min_stock_location
  FOREIGN KEY (location_id) REFERENCES iwm.warehouse_location(id);

ALTER TABLE iwm.stock_adjustment_request
  ADD CONSTRAINT IF NOT EXISTS fk_adj_req_location
  FOREIGN KEY (location_id) REFERENCES iwm.warehouse_location(id);
