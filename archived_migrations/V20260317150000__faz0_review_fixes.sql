-- F4: WarehouseLocation.description → TEXT (was VARCHAR(500))
-- Ref: location.md v2.0 — description: String (TEXT)
ALTER TABLE production.production_execution_warehouse_location
  ALTER COLUMN description TYPE TEXT;

-- F6: Batch.unit → add CHECK constraint (KG / MT / PIECE)
-- Ref: batch-production.md — unit: Enum KG / MT / PIECE
ALTER TABLE production.production_execution_batch
  ADD CONSTRAINT chk_batch_unit CHECK (unit IN ('KG', 'MT', 'PIECE'));
