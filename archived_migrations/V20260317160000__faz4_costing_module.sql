-- =============================================================================
-- Phase 4: Costing Module (FAZ 4)
-- Docs: cost-structure.md, price-list.md, cost-calculation.md, exchange-rate-history.md
-- Created: 2026-03-17
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS costing;

-- =============================================================================
-- 1. COST ITEMS (Global + Module-Specific)
--    System-defined — tenants cannot add rows
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.cost_item
(
    id               UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    -- tenant_id is NULL for GLOBAL system items (shared across all tenants).
    -- Tenant-specific MODULE_SPECIFIC items will have a non-null tenant_id.
    -- RLS policies must allow IS NULL tenant_id rows to pass through.
    tenant_id        UUID,
    uid              VARCHAR(100) UNIQUE,
    code             VARCHAR(50) NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    scope            VARCHAR(30) NOT NULL, -- GLOBAL | MODULE_SPECIFIC
    module_type      VARCHAR(50),          -- FIBER | YARN | FABRIC | DYE | null (global)
    calculation_base VARCHAR(30) NOT NULL, -- PER_KG | PER_HOUR | PER_UNIT | PERCENTAGE | FIXED
    display_order    INT                  DEFAULT 0,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 0
);

-- Seed: 8 Global cost items (tenant_id = NULL → visible to all tenants)
INSERT INTO costing.cost_item (id, tenant_id, code, name, scope, calculation_base, display_order)
VALUES
    (gen_random_uuid(), NULL, 'RAW_PRODUCT', 'Hammadde',       'GLOBAL', 'PER_KG',   1),
    (gen_random_uuid(), NULL, 'LABOR',        'İşçilik',         'GLOBAL', 'PER_HOUR',  2),
    (gen_random_uuid(), NULL, 'MACHINE',      'Makine/Ekipman', 'GLOBAL', 'PER_HOUR',  3),
    (gen_random_uuid(), NULL, 'ENERGY',       'Enerji',         'GLOBAL', 'PER_KG',   4),
    (gen_random_uuid(), NULL, 'OVERHEAD',     'Genel Gider',    'GLOBAL', 'PERCENTAGE',5),
    (gen_random_uuid(), NULL, 'LOGISTICS',    'Nakliye',        'GLOBAL', 'FIXED',     6),
    (gen_random_uuid(), NULL, 'QUALITY',      'Kalite Kontrol', 'GLOBAL', 'PER_UNIT',  7),
    (gen_random_uuid(), NULL, 'PACKAGING',    'Paketleme',      'GLOBAL', 'PER_UNIT',  8);

-- Module-specific items (also tenant_id = NULL → system defaults, tenants can override)
INSERT INTO costing.cost_item (id, tenant_id, code, name, scope, module_type, calculation_base, display_order)
VALUES
    (gen_random_uuid(), NULL, 'FIBER_SEPARATION', 'Elyaf Ayırma', 'MODULE_SPECIFIC', 'FIBER',   'PER_KG', 10),
    (gen_random_uuid(), NULL, 'FIBER_BALING',     'Balyalama',    'MODULE_SPECIFIC', 'FIBER',   'PER_KG', 11),
    (gen_random_uuid(), NULL, 'FIBER_MOISTURE',   'Nem Ayarı',    'MODULE_SPECIFIC', 'FIBER',   'PER_KG', 12),
    (gen_random_uuid(), NULL, 'YARN_TWIST',       'Büküm',        'MODULE_SPECIFIC', 'YARN',    'PER_KG', 13),
    (gen_random_uuid(), NULL, 'YARN_PLY',         'Katlama',      'MODULE_SPECIFIC', 'YARN',    'PER_KG', 14),
    (gen_random_uuid(), NULL, 'YARN_WINDING',     'Bobin Sarım', 'MODULE_SPECIFIC', 'YARN',    'PER_KG', 15),
    (gen_random_uuid(), NULL, 'FABRIC_WEAVING',   'Dokuma/Örgü',  'MODULE_SPECIFIC', 'FABRIC',  'PER_KG', 16),
    (gen_random_uuid(), NULL, 'FABRIC_SIZING',    'Haşıl',        'MODULE_SPECIFIC', 'FABRIC',  'PER_KG', 17),
    (gen_random_uuid(), NULL, 'FABRIC_WARPING',   'Çözgü',        'MODULE_SPECIFIC', 'FABRIC',  'PER_KG', 18),
    (gen_random_uuid(), NULL, 'DYE_CHEMICAL',     'Boya Kimyasalı','MODULE_SPECIFIC','DYE',    'PER_KG', 19),
    (gen_random_uuid(), NULL, 'DYE_WATER',        'Su Tüketimi',   'MODULE_SPECIFIC', 'DYE',    'PER_KG', 20),
    (gen_random_uuid(), NULL, 'DYE_FINISHING',    'Apre',         'MODULE_SPECIFIC', 'DYE',    'PER_KG', 21);

-- =============================================================================
-- 2. COST TEMPLATES (Tenant-configurable)
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.cost_template
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    uid         VARCHAR(100) UNIQUE,
    name        VARCHAR(255) NOT NULL,
    module_type VARCHAR(50)  NOT NULL,
    is_default  BOOLEAN      NOT NULL    DEFAULT FALSE,
    items       JSONB        NOT NULL    DEFAULT '[]'::JSONB,
    -- items format: [{"costItemCode": "RAW_PRODUCT", "weight": 0.6, "isIncluded": true}, ...]
    is_active   BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 3. PRICE LISTS
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.price_list
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    uid         VARCHAR(100) UNIQUE,
    name        VARCHAR(255) NOT NULL,
    module_type VARCHAR(50)  NOT NULL,
    currency    VARCHAR(10)  NOT NULL    DEFAULT 'TRY',
    valid_from  DATE         NOT NULL,
    valid_until DATE,
    is_active   BOOLEAN      NOT NULL    DEFAULT TRUE,
    season_tag  VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS costing.price_list_item
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID            NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    price_list_id      UUID            NOT NULL REFERENCES costing.price_list (id),
    cost_item_code     VARCHAR(50)     NOT NULL, -- FK → costing.cost_item.code (soft ref)
    product_id        UUID,                     -- nullable: product-specific
    trading_partner_id UUID,                     -- nullable: null=general, non-null=contracted
    unit_price         NUMERIC(18, 4)  NOT NULL,
    unit               VARCHAR(20)     NOT NULL,
    currency           VARCHAR(10)     NOT NULL  DEFAULT 'TRY',
    is_active          BOOLEAN         NOT NULL  DEFAULT TRUE,
    created_at         TIMESTAMPTZ     NOT NULL  DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ     NOT NULL  DEFAULT NOW(),
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT          NOT NULL  DEFAULT 0
);

CREATE TABLE IF NOT EXISTS costing.volume_price_break
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id         UUID            NOT NULL,
    uid               VARCHAR(100) UNIQUE,
    price_list_item_id UUID            NOT NULL REFERENCES costing.price_list_item (id),
    min_qty           NUMERIC(15, 3)  NOT NULL,
    max_qty           NUMERIC(15, 3),
    unit_price        NUMERIC(18, 4)  NOT NULL,
    discount_rate     NUMERIC(5, 4),            -- 0.0000 – 1.0000
    is_active         BOOLEAN         NOT NULL  DEFAULT TRUE,
    created_at        TIMESTAMPTZ     NOT NULL  DEFAULT NOW(),
    created_by        UUID,
    updated_at        TIMESTAMPTZ     NOT NULL  DEFAULT NOW(),
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT          NOT NULL  DEFAULT 0
);

-- =============================================================================
-- 4. EXCHANGE RATE SNAPSHOTS
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.exchange_rate_snapshot
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    base_currency   VARCHAR(10) NOT NULL    DEFAULT 'TRY',
    target_currency VARCHAR(10) NOT NULL,
    rate            NUMERIC(20, 8) NOT NULL,
    source          VARCHAR(20) NOT NULL,   -- TCMB | ECB | MANUAL
    captured_at     TIMESTAMPTZ NOT NULL,
    is_active       BOOLEAN     NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 5. COST HISTORY (Price trend analysis)
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.cost_history
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    tenant_id      UUID            NOT NULL,
    uid            VARCHAR(100) UNIQUE,
    cost_item_code VARCHAR(50)     NOT NULL,
    module_type    VARCHAR(50),
    product_id    UUID,
    unit_price     NUMERIC(18, 4)  NOT NULL,
    currency       VARCHAR(10)     NOT NULL DEFAULT 'TRY',
    valid_from     DATE            NOT NULL,
    valid_until    DATE,
    change_reason  TEXT,
    season_tag     VARCHAR(100),
    is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT          NOT NULL DEFAULT 0
);

-- =============================================================================
-- 6. COST CALCULATIONS (Polymorphic: QUOTE / WORK_ORDER / BATCH)
-- =============================================================================
CREATE TABLE IF NOT EXISTS costing.cost_calculation
(
    id                        UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id                 UUID            NOT NULL,
    uid                       VARCHAR(100) UNIQUE,
    entity_type               VARCHAR(30)     NOT NULL, -- QUOTE | WORK_ORDER | BATCH
    entity_id                 UUID            NOT NULL,
    module_type               VARCHAR(50)     NOT NULL,
    cost_template_id          UUID REFERENCES costing.cost_template (id),
    stage                     VARCHAR(20)     NOT NULL, -- ESTIMATED | PLANNED | ACTUAL
    total_cost                NUMERIC(18, 4)  NOT NULL DEFAULT 0,
    currency                  VARCHAR(10)     NOT NULL DEFAULT 'TRY',
    calculated_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    exchange_rate_snapshot_id UUID REFERENCES costing.exchange_rate_snapshot (id),
    notes                     TEXT,
    is_active                 BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by                UUID,
    deleted_at                TIMESTAMPTZ,
    version                   BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS costing.cost_calculation_line
(
    id                    UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    uid                   VARCHAR(100) UNIQUE,
    cost_calculation_id   UUID            NOT NULL REFERENCES costing.cost_calculation (id),
    cost_item_code        VARCHAR(50)     NOT NULL,
    qty                   NUMERIC(15, 3),
    unit                  VARCHAR(20),
    unit_price            NUMERIC(18, 4)  NOT NULL,
    currency              VARCHAR(10)     NOT NULL DEFAULT 'TRY',
    total_in_base_currency NUMERIC(18, 4) NOT NULL DEFAULT 0,
    exchange_rate         NUMERIC(20, 8),
    volume_discount_applied BOOLEAN       NOT NULL DEFAULT FALSE,
    notes                 TEXT,
    is_active             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version               BIGINT          NOT NULL DEFAULT 0
);

-- =============================================================================
-- INDEXES
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_cost_template_tenant ON costing.cost_template (tenant_id, module_type);
CREATE INDEX IF NOT EXISTS idx_price_list_tenant ON costing.price_list (tenant_id, is_active, valid_from);
CREATE INDEX IF NOT EXISTS idx_price_list_item_list ON costing.price_list_item (price_list_id, cost_item_code);
CREATE INDEX IF NOT EXISTS idx_price_list_item_partner ON costing.price_list_item (trading_partner_id) WHERE trading_partner_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_volume_break_item ON costing.volume_price_break (price_list_item_id);
CREATE INDEX IF NOT EXISTS idx_exchange_rate_currencies ON costing.exchange_rate_snapshot (base_currency, target_currency, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_history_item ON costing.cost_history (cost_item_code, product_id, valid_from);
CREATE INDEX IF NOT EXISTS idx_cost_calc_entity ON costing.cost_calculation (entity_type, entity_id, stage);
CREATE INDEX IF NOT EXISTS idx_cost_calc_line_calc ON costing.cost_calculation_line (cost_calculation_id);
