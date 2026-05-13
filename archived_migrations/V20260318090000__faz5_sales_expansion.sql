-- =============================================================================
-- Phase 5: Sales Expansion (FAZ 5)
-- Docs: sales-product.md, discount-policy.md, quote-approval.md, sample-management.md
-- Created: 2026-03-18
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS sales;

-- =============================================================================
-- 1. DISCOUNT POLICY (Tenant-level Kâr Marjı ve İndirim Kuralları)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sales.discount_policy
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    module_type         VARCHAR(50)  NOT NULL, -- FIBER | YARN | FABRIC | DYE_FINISHING
    base_discount_limit NUMERIC(5, 4) NOT NULL   DEFAULT 0.1000, -- %10
    min_profit_margin   NUMERIC(5, 4) NOT NULL   DEFAULT 0.0500, -- %5 (Kırmızı Çizgi)
    require_manager_above NUMERIC(5, 4) NOT NULL DEFAULT 0.1000, -- %10
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 2. PRODUCT CATALOG (Fuar / Offline Satış Katalog Ürünleri)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sales.sales_product
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    uid            VARCHAR(100) UNIQUE,
    product_id    UUID         NOT NULL, -- FK → production.product
    module_type    VARCHAR(50)  NOT NULL,
    list_price     NUMERIC(18, 4) NOT NULL, -- Baz liste fiyatı
    currency       VARCHAR(10)  NOT NULL    DEFAULT 'TRY',
    moq            NUMERIC(15, 3),          -- Minimum sipariş miktarı
    moq_unit       VARCHAR(20),             -- KG, MT, PIECE
    lead_time_days INT,                     -- Teslimat süresi (gün)
    specs          JSONB        NOT NULL    DEFAULT '{}'::JSONB,
    photos         JSONB        NOT NULL    DEFAULT '[]'::JSONB,
    is_active      BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 3. QUOTE & QUOTE_LINE (Satış Teklifi)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sales.quote
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    quote_number        VARCHAR(50)  NOT NULL UNIQUE, -- QT-2024-001 vb.
    customer_id         UUID         NOT NULL, -- FK → master_data.trading_partner
    assigned_to_id      UUID         NOT NULL, -- Pazarlamacı (FK → User)
    module_type         VARCHAR(50)  NOT NULL,
    status              VARCHAR(30)  NOT NULL    DEFAULT 'DRAFT', -- DRAFT, PENDING_APPROVAL vb.
    estimated_unit_cost NUMERIC(18, 4),        -- CostCalculation module'den
    valid_until         DATE         NOT NULL,
    payment_terms       VARCHAR(50),           -- CASH, NET_30 vb.
    lead_time_days      INT,
    notes               TEXT,
    attachments         JSONB        NOT NULL    DEFAULT '[]'::JSONB,
    revision_number     INT          NOT NULL    DEFAULT 1,
    parent_quote_id     UUID REFERENCES sales.quote (id), -- Revize edilen atası
    offline_created_at  TIMESTAMPTZ,
    device_id           VARCHAR(100),
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sales.quote_line
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    quote_id        UUID         NOT NULL REFERENCES sales.quote (id),
    product_id     UUID,                  -- katalog ürünü
    product_desc    TEXT,                  -- özel/serbest tanım
    requested_qty   NUMERIC(15, 3) NOT NULL,
    unit            VARCHAR(20)  NOT NULL,
    list_price      NUMERIC(18, 4) NOT NULL,
    offered_price   NUMERIC(18, 4) NOT NULL,
    discount_rate   NUMERIC(5, 4)  NOT NULL, -- Otomatik hesaplanır ([list-offered]/list)
    profit_margin   NUMERIC(5, 4),           -- Otomatik hesaplanır ([offered-cost]/offered)
    price_zone      VARCHAR(30)  NOT NULL, -- FREE | MANAGER_APPROVAL | BLOCKED
    module_specs    JSONB        NOT NULL    DEFAULT '{}'::JSONB,
    is_active       BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 4. QUOTE APPROVAL TOKEN (Müşteri Onay Linkleri)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sales.quote_approval_token
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    quote_id        UUID         NOT NULL REFERENCES sales.quote (id),
    token           VARCHAR(255) NOT NULL UNIQUE,
    channel         VARCHAR(30)  NOT NULL, -- EMAIL | WHATSAPP | IN_PERSON
    sent_to         VARCHAR(255),          -- E-posta adresi / tel no
    expires_at      TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(30)  NOT NULL    DEFAULT 'PENDING', -- PENDING | USED | EXPIRED
    used_at         TIMESTAMPTZ,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    location        JSONB,                 -- Enlem / Boylam lokasyon verisi
    is_active       BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 5. SAMPLE MANAGEMENT (Numune Talebi ve Teslimatı)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sales.sample_request
(
    id               UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    uid              VARCHAR(100) UNIQUE,
    customer_id      UUID         NOT NULL, -- FK → master_data.trading_partner
    product_id      UUID         NOT NULL, -- FK → production.product
    requested_qty    NUMERIC(15, 3) NOT NULL,
    unit             VARCHAR(20)  NOT NULL,
    delivery_method  VARCHAR(30)  NOT NULL, -- CARGO | SALESPERSON | DELIVERY_ROUTE
    delivery_address JSONB,                 -- Alıcı adresi
    status           VARCHAR(30)  NOT NULL    DEFAULT 'REQUESTED',
    sales_order_id   UUID,                  -- Sonradan siparişe dönerse
    notes            TEXT,
    is_active        BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sales.sample_delivery
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    uid               VARCHAR(100) UNIQUE,
    sample_request_id UUID         NOT NULL REFERENCES sales.sample_request (id),
    delivery_method   VARCHAR(30)  NOT NULL, -- CARGO | SALESPERSON
    tracking_number   VARCHAR(100),          -- Kargo numarası
    cargo_company     VARCHAR(100),          -- DHL, PTT vs.
    delivered_by_id   UUID,                  -- FK → User (eğer salesperson teslim ettiyse)
    dispatched_at     TIMESTAMPTZ,           -- Gönderim tarihi
    delivered_at      TIMESTAMPTZ,           -- Ulaşım tarihi
    recipient_name    VARCHAR(255),          -- Teslim alan kim?
    delivery_photo    TEXT,                  -- Teslimat kanıtı fotoğraf URL'si
    is_active         BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- INDEXES
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_sales_discount_policy_tenant ON sales.discount_policy(tenant_id, module_type);
CREATE INDEX IF NOT EXISTS idx_sales_sales_product_tenant ON sales.sales_product(tenant_id, module_type);
CREATE INDEX IF NOT EXISTS idx_sales_sales_product_product ON sales.sales_product(product_id);

CREATE INDEX IF NOT EXISTS idx_sales_quote_tenant_customer ON sales.quote(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_quote_number ON sales.quote(quote_number);
CREATE INDEX IF NOT EXISTS idx_sales_quote_line_quote ON sales.quote_line(quote_id);
CREATE INDEX IF NOT EXISTS idx_sales_quote_token ON sales.quote_approval_token(token);
CREATE INDEX IF NOT EXISTS idx_sales_quote_parent ON sales.quote(parent_quote_id);

CREATE INDEX IF NOT EXISTS idx_sales_sample_req_tenant_customer ON sales.sample_request(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_sample_delivery_req ON sales.sample_delivery(sample_request_id);
