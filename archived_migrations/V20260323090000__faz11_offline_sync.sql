-- Faz 11: Mobile & Offline Sync Migration
-- Adds OfflineMetadata embedded fields to relevant entities
-- CR-11-05: IF EXISTS guards for safe rename/drop

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. sales.quote — migrate old primitive columns → unified OfflineMetadata
-- ═══════════════════════════════════════════════════════════════════════════
DO $$
BEGIN
  -- Rename old columns if they exist (from early prototype)
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='sales' AND table_name='quote' AND column_name='offline_created_at') THEN
    ALTER TABLE sales.quote RENAME COLUMN offline_created_at TO offline_created_at_old;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='sales' AND table_name='quote' AND column_name='device_id') THEN
    ALTER TABLE sales.quote RENAME COLUMN device_id TO offline_device_id_old;
  END IF;
END $$;

ALTER TABLE sales.quote
  ADD COLUMN IF NOT EXISTS offline_id UUID,
  ADD COLUMN IF NOT EXISTS offline_device_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS sync_conflict_reason TEXT;

-- Migrate old data if renamed columns exist
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='sales' AND table_name='quote' AND column_name='offline_created_at_old') THEN
    UPDATE sales.quote
    SET offline_created_at = offline_created_at_old,
        offline_device_id = offline_device_id_old
    WHERE offline_created_at_old IS NOT NULL;

    ALTER TABLE sales.quote DROP COLUMN offline_created_at_old;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='sales' AND table_name='quote' AND column_name='offline_device_id_old') THEN
    ALTER TABLE sales.quote DROP COLUMN offline_device_id_old;
  END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. order.sales_order
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE "order".sales_order
  ADD COLUMN IF NOT EXISTS offline_id UUID,
  ADD COLUMN IF NOT EXISTS offline_device_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS sync_conflict_reason TEXT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. common_company.common_trading_partner
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE common_company.common_trading_partner
  ADD COLUMN IF NOT EXISTS offline_id UUID,
  ADD COLUMN IF NOT EXISTS offline_device_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS sync_conflict_reason TEXT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. sales.sample_request
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE sales.sample_request
  ADD COLUMN IF NOT EXISTS offline_id UUID,
  ADD COLUMN IF NOT EXISTS offline_device_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS sync_conflict_reason TEXT;

-- ═══════════════════════════════════════════════════════════════════════════
-- Indexes
-- ═══════════════════════════════════════════════════════════════════════════

-- CR-11-08: UNIQUE partial indexes on offline_id (idempotent sync support)
CREATE UNIQUE INDEX IF NOT EXISTS uk_quote_offline_id
  ON sales.quote(offline_id) WHERE offline_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_so_offline_id
  ON "order".sales_order(offline_id) WHERE offline_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_tp_offline_id
  ON common_company.common_trading_partner(offline_id) WHERE offline_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sr_offline_id
  ON sales.sample_request(offline_id) WHERE offline_id IS NOT NULL;

-- Partial indexes for fast PENDING/CONFLICT queue queries
CREATE INDEX IF NOT EXISTS idx_quote_sync_status
  ON sales.quote(sync_status) WHERE sync_status IN ('PENDING', 'CONFLICT');
CREATE INDEX IF NOT EXISTS idx_so_sync_status
  ON "order".sales_order(sync_status) WHERE sync_status IN ('PENDING', 'CONFLICT');
CREATE INDEX IF NOT EXISTS idx_tp_sync_status
  ON common_company.common_trading_partner(sync_status) WHERE sync_status IN ('PENDING', 'CONFLICT');
CREATE INDEX IF NOT EXISTS idx_sr_sync_status
  ON sales.sample_request(sync_status) WHERE sync_status IN ('PENDING', 'CONFLICT');
