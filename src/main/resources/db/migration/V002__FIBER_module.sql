-- ============================================
-- MODULE: FIBER
-- Birleştirilen migration'lar: V008, V009, V025, V070, V075, V080, V082
-- Not: prod_fiber_composition, prod_fiber_attribute_link, prod_fiber_certification_link (V070'te DROP).
-- Not: prod_fiber_specification (V082'de prod_fiber_quality_standard ile değiştirildi).
-- Not: production_quality_fiber_test_result → V007 IWM'de (batch_id FK nedeniyle).
-- INSERT'ler V010__SEEDS.sql ve R__001__fiber_seeds.sql içinde.
-- ============================================

CREATE SCHEMA IF NOT EXISTS production;

-- =====================================================
-- prod_fiber_category
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FCAT-00000',
    category_code VARCHAR(50) UNIQUE NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_category_code ON production.prod_fiber_category(category_code);
CREATE INDEX idx_fiber_category_active ON production.prod_fiber_category(is_active) WHERE is_active = TRUE;

-- =====================================================
-- prod_fiber_attribute
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FATR-00000',
    attribute_code VARCHAR(50) UNIQUE NOT NULL,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_group VARCHAR(50),
    description TEXT,
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_attribute_code ON production.prod_fiber_attribute(attribute_code);
CREATE INDEX idx_fiber_attribute_active ON production.prod_fiber_attribute(is_active) WHERE is_active = TRUE;

-- =====================================================
-- prod_fiber_certification
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FCER-00000',
    certification_code VARCHAR(50) UNIQUE NOT NULL,
    certification_name VARCHAR(100) NOT NULL,
    certifying_body VARCHAR(255),
    description TEXT,
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_certification_code ON production.prod_fiber_certification(certification_code);
CREATE INDEX idx_fiber_certification_active ON production.prod_fiber_certification(is_active) WHERE is_active = TRUE;

-- =====================================================
-- prod_fiber_iso_code
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_iso_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FISO-00000',
    iso_code VARCHAR(10) UNIQUE NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    fiber_type VARCHAR(100),
    description TEXT,
    is_official_iso BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_iso_code ON production.prod_fiber_iso_code(iso_code);
CREATE INDEX idx_fiber_iso_active ON production.prod_fiber_iso_code(is_active) WHERE is_active = TRUE;

-- =====================================================
-- prod_material (base catalog)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    material_type VARCHAR(20) NOT NULL CHECK (material_type IN ('FIBER', 'YARN', 'FABRIC', 'CHEMICAL', 'CONSUMABLE')),
    unit VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_material_tenant_type ON production.prod_material(tenant_id, material_type);
CREATE INDEX idx_material_active ON production.prod_material(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_material_tenant_active ON production.prod_material(tenant_id, is_active) WHERE is_active = TRUE;

-- =====================================================
-- prod_fiber (final: composition JSONB, no fiber_grade, category/iso NOT NULL)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    material_id UUID NOT NULL UNIQUE REFERENCES production.prod_material(id) ON DELETE CASCADE,
    fiber_category_id UUID NOT NULL REFERENCES production.prod_fiber_category(id),
    fiber_iso_code_id UUID NOT NULL REFERENCES production.prod_fiber_iso_code(id),
    fiber_name VARCHAR(255) NOT NULL,
    composition JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'OBSOLETE')),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_tenant ON production.prod_fiber(tenant_id);
CREATE INDEX idx_fiber_material ON production.prod_fiber(material_id);
CREATE INDEX idx_fiber_category ON production.prod_fiber(fiber_category_id);
CREATE INDEX idx_fiber_iso ON production.prod_fiber(fiber_iso_code_id);
CREATE INDEX idx_fiber_status ON production.prod_fiber(status);
CREATE INDEX idx_fiber_tenant_active ON production.prod_fiber(tenant_id, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_fiber_composition_gin ON production.prod_fiber USING GIN (composition);

-- =====================================================
-- prod_fiber_quality_standard (V082; replaces prod_fiber_specification)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_quality_standard (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    uid                     VARCHAR(100)    NOT NULL,
    iso_code_id             UUID            NOT NULL REFERENCES production.prod_fiber_iso_code(id),
    standard_name           VARCHAR(100)    NOT NULL,
    is_default              BOOLEAN         NOT NULL DEFAULT false,
    fineness_min            DOUBLE PRECISION,
    fineness_target         DOUBLE PRECISION,
    fineness_max            DOUBLE PRECISION,
    length_mm_min            DOUBLE PRECISION,
    length_mm_target         DOUBLE PRECISION,
    length_mm_max            DOUBLE PRECISION,
    strength_cnd_tex_min     DOUBLE PRECISION,
    strength_cnd_tex_target  DOUBLE PRECISION,
    strength_cnd_tex_max     DOUBLE PRECISION,
    elongation_pct_min       DOUBLE PRECISION,
    elongation_pct_target    DOUBLE PRECISION,
    elongation_pct_max       DOUBLE PRECISION,
    moisture_pct_min         DOUBLE PRECISION,
    moisture_pct_target      DOUBLE PRECISION,
    moisture_pct_max         DOUBLE PRECISION,
    trash_content_pct_min    DOUBLE PRECISION,
    trash_content_pct_target DOUBLE PRECISION,
    trash_content_pct_max    DOUBLE PRECISION,
    is_active                BOOLEAN        NOT NULL DEFAULT true,
    deleted_at               TIMESTAMP WITH TIME ZONE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_fiber_quality_standard PRIMARY KEY (id),
    CONSTRAINT fk_fiber_quality_standard_iso_code FOREIGN KEY (iso_code_id) REFERENCES production.prod_fiber_iso_code(id),
    CONSTRAINT uq_fiber_quality_standard_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_fiber_quality_standard_tenant_iso_name UNIQUE (tenant_id, iso_code_id, standard_name),
    CONSTRAINT ck_fqs_fineness_range CHECK (fineness_min IS NULL OR fineness_max IS NULL OR fineness_min <= fineness_max),
    CONSTRAINT ck_fqs_length_range CHECK (length_mm_min IS NULL OR length_mm_max IS NULL OR length_mm_min <= length_mm_max),
    CONSTRAINT ck_fqs_strength_range CHECK (strength_cnd_tex_min IS NULL OR strength_cnd_tex_max IS NULL OR strength_cnd_tex_min <= strength_cnd_tex_max),
    CONSTRAINT ck_fqs_elongation_range CHECK (elongation_pct_min IS NULL OR elongation_pct_max IS NULL OR elongation_pct_min <= elongation_pct_max),
    CONSTRAINT ck_fqs_moisture_range CHECK (moisture_pct_min IS NULL OR moisture_pct_max IS NULL OR moisture_pct_min <= moisture_pct_max),
    CONSTRAINT ck_fqs_trash_range CHECK (trash_content_pct_min IS NULL OR trash_content_pct_max IS NULL OR trash_content_pct_min <= trash_content_pct_max)
);

CREATE INDEX idx_fiber_quality_standard_tenant ON production.prod_fiber_quality_standard(tenant_id);
CREATE INDEX idx_fiber_quality_standard_iso ON production.prod_fiber_quality_standard(tenant_id, iso_code_id, is_default);

-- =====================================================
-- production_fiber_request (tenant request for new fiber; FK to common_tenant)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.production_fiber_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    requested_by UUID NOT NULL,
    iso_code VARCHAR(20) NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    fiber_type VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    review_note TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiber_request_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fiber_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_fiber_request_tenant ON production.production_fiber_request(tenant_id);
CREATE INDEX idx_fiber_request_status ON production.production_fiber_request(status);
CREATE INDEX idx_fiber_request_requested_by ON production.production_fiber_request(requested_by);
CREATE INDEX idx_fiber_request_active ON production.production_fiber_request(is_active) WHERE is_active = TRUE;

-- [FIBER] module migration tamamlandı.
-- Tablo sayısı: 8
-- Toplam index sayısı: 28+
