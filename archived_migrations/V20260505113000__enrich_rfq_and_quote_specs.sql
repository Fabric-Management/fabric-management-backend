-- procurement.supplier_quote tablosuna module_type eklenmesi
ALTER TABLE procurement.supplier_quote ADD COLUMN module_type VARCHAR(50);
UPDATE procurement.supplier_quote SET module_type = 'GENERIC' WHERE module_type IS NULL;
ALTER TABLE procurement.supplier_quote ALTER COLUMN module_type SET NOT NULL;

-- procurement.supplier_quote_line tablosuna module_specs eklenmesi
ALTER TABLE procurement.supplier_quote_line ADD COLUMN module_specs JSONB NOT NULL DEFAULT '{"specType":"GENERIC"}'::jsonb;
