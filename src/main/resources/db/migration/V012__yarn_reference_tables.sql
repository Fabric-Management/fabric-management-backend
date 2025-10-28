-- =====================================================
-- YARN: Reference Tables (System-Defined)
-- =====================================================
-- These tables define yarn categories, attributes, and certifications
-- All data is system-level (SYSTEM_TENANT_ID) and read-only

-- =====================================================
-- YARN CATEGORY
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_category_tenant_id ON production.prod_yarn_category(tenant_id);
CREATE INDEX idx_yarn_category_code ON production.prod_yarn_category(category_code);

INSERT INTO production.prod_yarn_category (tenant_id, uid, category_code, category_name, description, display_order)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-001', 'SEWING', 'Sewing Yarn', 'Yarn used for sewing operations', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-002', 'KNITTING', 'Knitting Yarn', 'Yarn used for knitting fabric', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-003', 'WEAVING', 'Weaving Yarn', 'Yarn used for weaving fabric on loom', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-004', 'EMBROIDERY', 'Embroidery Yarn', 'Decorative embroidery yarn', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-005', 'SPECIALTY', 'Specialty Yarn', 'Special purpose yarn (industrial, technical)', 5);

-- =====================================================
-- YARN ATTRIBUTE
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_attribute_tenant_id ON production.prod_yarn_attribute(tenant_id);
CREATE INDEX idx_yarn_attribute_code ON production.prod_yarn_attribute(attribute_code);

INSERT INTO production.prod_yarn_attribute (tenant_id, uid, attribute_code, attribute_name, attribute_type, unit)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-001', 'COUNT', 'Yarn Count', 'PHYSICAL', 'Ne/Tex'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-002', 'TWIST', 'Twist per Meter', 'PHYSICAL', 'TPM'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-003', 'STRENGTH', 'Tensile Strength', 'MECHANICAL', 'cN/tex'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-004', 'ELONGATION', 'Elongation at Break', 'MECHANICAL', '%'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-005', 'HAIRINESS', 'Yarn Hairiness', 'PHYSICAL', 'H-value'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-006', 'EVENNESS', 'Yarn Evenness', 'QUALITY', 'CV%');

-- =====================================================
-- YARN CERTIFICATION
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_yarn_certification_tenant_id ON production.prod_yarn_certification(tenant_id);
CREATE INDEX idx_yarn_certification_code ON production.prod_yarn_certification(certification_code);

INSERT INTO production.prod_yarn_certification (tenant_id, uid, certification_code, certification_name, certifying_body)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-001', 'GOTS', 'Global Organic Textile Standard', 'GOTS'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-002', 'OEKO_TEX', 'Oeko-Tex Standard 100', 'OEKO-TEX'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-003', 'GRS', 'Global Recycled Standard', 'Textile Exchange'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-004', 'BSCI', 'Business Social Compliance Initiative', 'BSCI');

COMMENT ON TABLE production.prod_yarn_category IS 'System-defined yarn categories (sewing, knitting, weaving, etc.)';
COMMENT ON TABLE production.prod_yarn_attribute IS 'Physical, mechanical, and quality attributes for yarn';
COMMENT ON TABLE production.prod_yarn_certification IS 'Certification standards for yarn (GOTS, OEKO-TEX, etc.)';

