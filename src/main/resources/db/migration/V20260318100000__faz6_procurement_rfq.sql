-- =============================================================================
-- Phase 6: Supplier RFQ + Quote
-- Docs: supplier-rfq.md, supplier-quote.md
-- Created: 2026-03-18
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS procurement;

-- =============================================================================
-- 1. SUPPLIER RFQ
-- =============================================================================
CREATE TABLE IF NOT EXISTS procurement.supplier_rfq
(
    id               UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    uid              VARCHAR(100) UNIQUE,
    rfq_number       VARCHAR(50)  NOT NULL UNIQUE,
    work_order_id    UUID         NOT NULL, -- FK → production.work_order
    module_type      VARCHAR(50)  NOT NULL, -- FIBER, YARN vs.
    rfq_type         VARCHAR(30)  NOT NULL, -- PURCHASE, SUBCONTRACT
    status           VARCHAR(30)  NOT NULL    DEFAULT 'DRAFT',
    deadline         TIMESTAMPTZ  NOT NULL,
    notes            TEXT,
    attachments      JSONB        NOT NULL    DEFAULT '[]'::JSONB,
    is_active        BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS procurement.supplier_rfq_recipient
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    rfq_id             UUID         NOT NULL REFERENCES procurement.supplier_rfq (id),
    trading_partner_id UUID         NOT NULL, -- FK → master_data.trading_partner
    sent_at            TIMESTAMPTZ,
    status             VARCHAR(30)  NOT NULL    DEFAULT 'SENT', -- SENT, QUOTE_RECEIVED, NO_RESPONSE
    response_deadline  TIMESTAMPTZ,
    is_active          BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS procurement.supplier_rfq_line
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    uid            VARCHAR(100) UNIQUE,
    rfq_id         UUID         NOT NULL REFERENCES procurement.supplier_rfq (id),
    material_id    UUID,                  -- FK → production.material
    product_desc   TEXT,
    requested_qty  NUMERIC(15, 3) NOT NULL,
    unit           VARCHAR(20)  NOT NULL,
    module_specs   JSONB        NOT NULL    DEFAULT '{}'::JSONB,
    is_active      BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 2. SUPPLIER QUOTE
-- =============================================================================
CREATE TABLE IF NOT EXISTS procurement.supplier_quote
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    quote_number       VARCHAR(50)  NOT NULL UNIQUE,
    rfq_id             UUID         NOT NULL REFERENCES procurement.supplier_rfq (id),
    trading_partner_id UUID         NOT NULL,
    status             VARCHAR(30)  NOT NULL    DEFAULT 'RECEIVED', -- RECEIVED, ACCEPTED, REJECTED, EXPIRED
    valid_until        DATE         NOT NULL,
    currency           VARCHAR(10)  NOT NULL    DEFAULT 'TRY',
    payment_terms      VARCHAR(50),
    lead_time_days     INT,
    entry_method       VARCHAR(30)  NOT NULL, -- PORTAL, MANUAL_ENTRY
    notes              TEXT,
    attachments        JSONB        NOT NULL    DEFAULT '[]'::JSONB,
    submitted_at       TIMESTAMPTZ,
    is_active          BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS procurement.supplier_quote_line
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    supplier_quote_id  UUID         NOT NULL REFERENCES procurement.supplier_quote (id),
    rfq_line_id        UUID         NOT NULL REFERENCES procurement.supplier_rfq_line (id),
    unit_price         NUMERIC(18, 4) NOT NULL,
    currency           VARCHAR(10)  NOT NULL,
    qty                NUMERIC(15, 3) NOT NULL,
    unit               VARCHAR(20)  NOT NULL,
    volume_discounts   JSONB        NOT NULL    DEFAULT '{}'::JSONB,
    notes              TEXT,
    is_active          BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS procurement.supplier_quote_token
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    rfq_recipient_id   UUID         NOT NULL REFERENCES procurement.supplier_rfq_recipient (id),
    token              VARCHAR(255) NOT NULL UNIQUE,
    expires_at         TIMESTAMPTZ  NOT NULL,
    status             VARCHAR(30)  NOT NULL    DEFAULT 'PENDING', -- PENDING, USED, EXPIRED
    used_at            TIMESTAMPTZ,
    entry_method       VARCHAR(30), -- PORTAL, EMAIL
    is_active          BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL    DEFAULT 0
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_supplier_rfq_tenant on procurement.supplier_rfq(tenant_id);
CREATE INDEX IF NOT EXISTS idx_supplier_rfq_wo on procurement.supplier_rfq(work_order_id);
CREATE INDEX IF NOT EXISTS idx_supplier_rfq_recipient_rfq on procurement.supplier_rfq_recipient(rfq_id);
CREATE INDEX IF NOT EXISTS idx_supplier_rfq_line_rfq on procurement.supplier_rfq_line(rfq_id);

CREATE INDEX IF NOT EXISTS idx_supplier_quote_tenant on procurement.supplier_quote(tenant_id);
CREATE INDEX IF NOT EXISTS idx_supplier_quote_rfq on procurement.supplier_quote(rfq_id);
CREATE INDEX IF NOT EXISTS idx_supplier_quote_partner on procurement.supplier_quote(trading_partner_id);
CREATE INDEX IF NOT EXISTS idx_supplier_quote_line_quote on procurement.supplier_quote_line(supplier_quote_id);
CREATE INDEX IF NOT EXISTS idx_supplier_quote_token_val on procurement.supplier_quote_token(token);
