-- Sprint 7b: Multi-Currency Support
-- 1. Organization: reporting_currency
ALTER TABLE common_tenant.organization ADD COLUMN IF NOT EXISTS reporting_currency VARCHAR(3);

-- 2. ExchangeRateCache table (column names match ExchangeRateCache.java entity)
CREATE TABLE IF NOT EXISTS costing.exchange_rate_cache (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    base_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(15, 6) NOT NULL,
    rate_date DATE NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exchange_rate_tenant_pair_date UNIQUE (tenant_id, base_currency, target_currency, rate_date)
);
CREATE INDEX IF NOT EXISTS idx_exchange_rate_cache_tenant ON costing.exchange_rate_cache(tenant_id);

-- 3. CostCalculationLine: ConvertedMoney embedding
ALTER TABLE costing.cost_calculation_line 
ADD COLUMN IF NOT EXISTS original_line_total NUMERIC(18, 4),
ADD COLUMN IF NOT EXISTS original_currency VARCHAR(10),
ADD COLUMN IF NOT EXISTS converted_line_total NUMERIC(18, 4),
ADD COLUMN IF NOT EXISTS reporting_currency VARCHAR(10),
ADD COLUMN IF NOT EXISTS exchange_rate_used NUMERIC(20, 8),
ADD COLUMN IF NOT EXISTS exchange_rate_date DATE;
