-- ============================================
-- MODULE: TRADING
-- Kaynak: V039, V040, V043, V048, V066, V071, V072
-- Not: V040 veri migrasyonu consolidated'da yok. V066: sales_order "order" şemasında.
-- ============================================

-- ============================================================================
-- 1. trading_partner_registry (platform-level; linked_tenant_id → common_tenant)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.trading_partner_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(100) UNIQUE NOT NULL,
    tax_id VARCHAR(50),
    official_name VARCHAR(255) NOT NULL,
    country VARCHAR(3),
    verified_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    linked_tenant_id UUID,
    verification_date TIMESTAMP,
    verified_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tpr_linked_tenant FOREIGN KEY (linked_tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tpr_tax_country ON common_company.trading_partner_registry(tax_id, country) WHERE tax_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tpr_linked_tenant ON common_company.trading_partner_registry(linked_tenant_id) WHERE linked_tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tpr_verified ON common_company.trading_partner_registry(verified_status);

-- ============================================================================
-- 2. common_trading_partner (tenant_id → common_tenant; organization_id → common_organization, V048)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_trading_partner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    registry_id UUID NOT NULL,
    custom_name VARCHAR(255),
    partner_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    relationship_meta JSONB,
    legacy_company_id UUID,
    organization_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tp_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tp_registry FOREIGN KEY (registry_id) REFERENCES common_company.trading_partner_registry(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tp_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE SET NULL,
    CONSTRAINT uk_tp_tenant_registry UNIQUE (tenant_id, registry_id)
);

CREATE INDEX IF NOT EXISTS idx_tp_tenant ON common_company.common_trading_partner(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tp_registry ON common_company.common_trading_partner(registry_id);
CREATE INDEX IF NOT EXISTS idx_tp_organization ON common_company.common_trading_partner(organization_id) WHERE organization_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tp_type ON common_company.common_trading_partner(partner_type);
CREATE INDEX IF NOT EXISTS idx_tp_meta ON common_company.common_trading_partner USING GIN (relationship_meta);

-- ============================================================================
-- 3. finance schema + finance_invoice
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS finance;

CREATE TABLE IF NOT EXISTS finance.finance_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    trading_partner_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    order_reference VARCHAR(100),
    external_reference VARCHAR(100),
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'SALES',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    payment_date DATE,
    subtotal NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) DEFAULT 0,
    discount_amount NUMERIC(19,4) DEFAULT 0,
    total_amount NUMERIC(19,4) NOT NULL,
    amount_paid NUMERIC(19,4) DEFAULT 0,
    amount_due NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    tax_rate NUMERIC(5,2),
    billing_address VARCHAR(500),
    notes TEXT,
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inv_trading_partner FOREIGN KEY (trading_partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    CONSTRAINT uk_inv_tenant_invoice_number UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX IF NOT EXISTS idx_inv_tenant ON finance.finance_invoice(tenant_id);
CREATE INDEX IF NOT EXISTS idx_inv_trading_partner ON finance.finance_invoice(trading_partner_id);
CREATE INDEX IF NOT EXISTS idx_inv_status ON finance.finance_invoice(status);
CREATE INDEX IF NOT EXISTS idx_inv_issue_date ON finance.finance_invoice(issue_date);

-- ============================================================================
-- 4. order schema + sales_order (V066: table in "order" schema)
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS "order";

CREATE TABLE IF NOT EXISTS "order".sales_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    trading_partner_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    customer_reference VARCHAR(100),
    order_type VARCHAR(20) NOT NULL DEFAULT 'SALES',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    order_date DATE NOT NULL,
    requested_delivery_date DATE,
    promised_delivery_date DATE,
    actual_delivery_date DATE,
    total_amount NUMERIC(19,4),
    tax_amount NUMERIC(19,4),
    discount_amount NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    shipping_address VARCHAR(500),
    billing_address VARCHAR(500),
    shipping_method VARCHAR(50),
    notes TEXT,
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_so_trading_partner FOREIGN KEY (trading_partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    CONSTRAINT uk_so_tenant_order_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX IF NOT EXISTS idx_so_tenant ON "order".sales_order(tenant_id);
CREATE INDEX IF NOT EXISTS idx_so_trading_partner ON "order".sales_order(trading_partner_id);
CREATE INDEX IF NOT EXISTS idx_so_status ON "order".sales_order(status);
CREATE INDEX IF NOT EXISTS idx_so_order_date ON "order".sales_order(order_date);

-- ============================================================================
-- 5. partner_trading_partner_certification (FK prod_fiber_certification → V002)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.partner_trading_partner_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    trading_partner_id UUID NOT NULL,
    certification_id UUID NOT NULL,
    license_no VARCHAR(100),
    issued_at DATE,
    valid_until DATE,
    document_ref VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ptpc_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ptpc_trading_partner FOREIGN KEY (trading_partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptpc_certification FOREIGN KEY (certification_id) REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ptpc_trading_partner ON common_company.partner_trading_partner_certification(trading_partner_id);
CREATE INDEX idx_ptpc_certification ON common_company.partner_trading_partner_certification(certification_id);

-- ============================================================================
-- 6. organization_certification (FK prod_fiber_certification → V002)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.organization_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    organization_id UUID NOT NULL,
    certification_id UUID NOT NULL,
    license_no VARCHAR(100),
    issued_at DATE,
    valid_until DATE,
    document_ref VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_oc_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oc_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_oc_certification FOREIGN KEY (certification_id) REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT
);

CREATE INDEX idx_oc_organization ON common_company.organization_certification(organization_id);
CREATE INDEX idx_oc_certification ON common_company.organization_certification(certification_id);

-- [TRADING] module migration tamamlandı.
