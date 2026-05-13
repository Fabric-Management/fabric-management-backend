-- Migration: sprint12_subcontract_product
-- E12: SubcontractOrder product type conversion

ALTER TABLE procurement.subcontract_order
  ADD COLUMN input_product_type VARCHAR(30),
  ADD COLUMN output_product_id UUID,
  ADD COLUMN output_product_type VARCHAR(30),
  ADD COLUMN expected_output_qty NUMERIC(15,3),
  ADD COLUMN output_unit VARCHAR(20);
