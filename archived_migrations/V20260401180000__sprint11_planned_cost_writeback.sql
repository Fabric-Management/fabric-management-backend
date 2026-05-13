-- V20260401180000__sprint11_planned_cost_writeback.sql
ALTER TABLE production.prod_work_order 
    ADD COLUMN IF NOT EXISTS planned_cost NUMERIC(15,3);

ALTER TABLE production.prod_work_order 
    ADD COLUMN IF NOT EXISTS planned_cost_currency VARCHAR(3);

-- Backfill: mevcut WO'lar için unit_cost * planned_qty hesapla
UPDATE production.prod_work_order 
SET planned_cost = unit_cost * planned_qty,
    planned_cost_currency = currency
WHERE planned_cost IS NULL 
  AND unit_cost IS NOT NULL 
  AND planned_qty IS NOT NULL
  AND is_active = true;

COMMENT ON COLUMN production.prod_work_order.planned_cost IS 
    'Written by CostCalculationService.computePlanned() — Sprint 11';
