-- ============================================
-- MODULE: YARN
-- Birleştirilen migration'lar: V012
-- INSERT'ler V010__SEEDS.sql içine taşındı.
-- ============================================

-- Production schema is created in V002 FIBER; reused here for yarn reference tables.

-- =====================================================
-- prod_yarn_category
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_yarn_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) NOT NULL,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_category_tenant_id ON production.prod_yarn_category(tenant_id);
CREATE INDEX idx_yarn_category_code ON production.prod_yarn_category(category_code);

-- =====================================================
-- prod_yarn_attribute
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_yarn_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) NOT NULL,
    attribute_code VARCHAR(50) NOT NULL UNIQUE,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_type VARCHAR(50) NOT NULL,
    unit VARCHAR(20),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_attribute_tenant_id ON production.prod_yarn_attribute(tenant_id);
CREATE INDEX idx_yarn_attribute_code ON production.prod_yarn_attribute(attribute_code);

-- =====================================================
-- prod_yarn_certification
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_yarn_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) NOT NULL,
    certification_code VARCHAR(50) NOT NULL UNIQUE,
    certification_name VARCHAR(100) NOT NULL,
    certifying_body VARCHAR(100),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_certification_tenant_id ON production.prod_yarn_certification(tenant_id);
CREATE INDEX idx_yarn_certification_code ON production.prod_yarn_certification(certification_code);

COMMENT ON TABLE production.prod_yarn_category IS 'System-defined yarn categories (sewing, knitting, weaving, etc.)';
COMMENT ON TABLE production.prod_yarn_attribute IS 'Physical, mechanical, and quality attributes for yarn';
COMMENT ON TABLE production.prod_yarn_certification IS 'Certification standards for yarn (GOTS, OEKO-TEX, etc.)';

-- [YARN] module migration tamamlandı.
-- Tablo sayısı: 3
-- Toplam index sayısı: 8
