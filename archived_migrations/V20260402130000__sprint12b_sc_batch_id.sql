-- E12b: Add batch_id to subcontract_order

ALTER TABLE procurement.subcontract_order
  ADD COLUMN batch_id UUID;
