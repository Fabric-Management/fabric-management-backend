-- 1) Tutar eşik alanları eklenmesi
ALTER TABLE common_approval.approval_policy
  ADD COLUMN min_amount_threshold DECIMAL(18, 3) DEFAULT NULL,
  ADD COLUMN max_amount_threshold DECIMAL(18, 3) DEFAULT NULL,
  ADD COLUMN currency VARCHAR(3) DEFAULT NULL;

-- 2) Mevcut unique constraint güncellenmesi
-- Eski: (tenant_id, entity_type, required_for_level)
-- Yeni: (tenant_id, entity_type, required_for_level, min_amount_threshold)
ALTER TABLE common_approval.approval_policy
  DROP CONSTRAINT IF EXISTS uk_approval_policy_entity_target;

-- Partial unique index: null min_amount_threshold'lar için ayrı unique
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_policy_no_amount
  ON common_approval.approval_policy (tenant_id, entity_type, required_for_level)
  WHERE min_amount_threshold IS NULL AND is_active = true AND deleted_at IS NULL;

-- Tutar aralıklı politikalar için unique
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_policy_with_amount
  ON common_approval.approval_policy (tenant_id, entity_type, required_for_level, min_amount_threshold)
  WHERE min_amount_threshold IS NOT NULL AND is_active = true AND deleted_at IS NULL;

-- 3) CHECK constraint: min <= max
ALTER TABLE common_approval.approval_policy
  ADD CONSTRAINT chk_amount_range
  CHECK (
    (min_amount_threshold IS NULL AND max_amount_threshold IS NULL)
    OR (min_amount_threshold IS NOT NULL AND max_amount_threshold IS NOT NULL
        AND min_amount_threshold <= max_amount_threshold)
  );

-- 4) Tutar aralığı + para birimi tutarlılığı
ALTER TABLE common_approval.approval_policy
  ADD CONSTRAINT chk_currency_with_amount
  CHECK (
    (min_amount_threshold IS NULL AND currency IS NULL)
    OR (min_amount_threshold IS NOT NULL AND currency IS NOT NULL)
  );

COMMENT ON COLUMN common_approval.approval_policy.min_amount_threshold IS 'Minimum tutar eşiği (dahil). NULL = tutar bazlı değil';
COMMENT ON COLUMN common_approval.approval_policy.max_amount_threshold IS 'Maksimum tutar eşiği (dahil).';
COMMENT ON COLUMN common_approval.approval_policy.currency IS 'ISO 4217 para birimi kodu (TRY, USD, EUR)';
