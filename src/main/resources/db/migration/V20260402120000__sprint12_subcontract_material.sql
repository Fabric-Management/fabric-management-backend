-- Migration: sprint12_subcontract_material
-- E12: SubcontractOrder material type conversion

ALTER TABLE procurement.subcontract_order
  ADD COLUMN input_material_type VARCHAR(30),
  ADD COLUMN output_material_id UUID,
  ADD COLUMN output_material_type VARCHAR(30),
  ADD COLUMN expected_output_qty NUMERIC(15,3),
  ADD COLUMN output_unit VARCHAR(20);
