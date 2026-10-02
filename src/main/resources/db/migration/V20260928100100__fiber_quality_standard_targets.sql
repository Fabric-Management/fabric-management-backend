-- ============================================================================
-- FIBER-CATALOG-1 (product/fiber): tenant quality profiles target a shared ISO code or an
-- exact fibre/mixture.
-- ============================================================================
-- ISO_CODE: shared iso_code_id required, applies to pure fibres of that ISO only.
-- FIBER:    visible fiber_id required plus the immutable normalised composition captured when the
--           profile was created ({fiberId: 100} for a pure fibre, the full map for a blend).
-- The profile is always tenant-owned, even when its target is a shared record. Development data
-- only: existing ISO profiles become ISO_CODE profiles; no other backfill is attempted.
-- ============================================================================

ALTER TABLE production.prod_fiber_quality_standard
    ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'ISO_CODE',
    ADD COLUMN fiber_id UUID REFERENCES production.prod_fiber (id),
    ADD COLUMN target_composition JSONB;

ALTER TABLE production.prod_fiber_quality_standard
    ALTER COLUMN target_type DROP DEFAULT,
    ALTER COLUMN iso_code_id DROP NOT NULL;

ALTER TABLE production.prod_fiber_quality_standard
    DROP CONSTRAINT IF EXISTS uq_fiber_quality_standard_tenant_iso_name;

ALTER TABLE production.prod_fiber_quality_standard
    ADD CONSTRAINT chk_fqs_target_type
        CHECK (target_type IN ('ISO_CODE', 'FIBER')),
    ADD CONSTRAINT chk_fqs_target_fields
        CHECK (
            (target_type = 'ISO_CODE'
                AND iso_code_id IS NOT NULL
                AND fiber_id IS NULL
                AND target_composition IS NULL)
            OR (target_type = 'FIBER'
                AND fiber_id IS NOT NULL
                AND iso_code_id IS NULL
                AND target_composition IS NOT NULL
                AND jsonb_typeof(target_composition) = 'object'
                AND target_composition <> '{}'::jsonb)
        ),
    ADD CONSTRAINT chk_fqs_standard_name_trimmed
        CHECK (standard_name = btrim(standard_name) AND standard_name <> ''),
    -- A profile without any criterion would approve every measurement automatically.
    ADD CONSTRAINT chk_fqs_has_criterion
        CHECK (num_nonnulls(
            fineness_min, fineness_target, fineness_max,
            length_mm_min, length_mm_target, length_mm_max,
            strength_cnd_tex_min, strength_cnd_tex_target, strength_cnd_tex_max,
            elongation_pct_min, elongation_pct_target, elongation_pct_max,
            moisture_pct_min, moisture_pct_target, moisture_pct_max,
            trash_content_pct_min, trash_content_pct_target, trash_content_pct_max,
            uniformity_index_min, uniformity_index_target, uniformity_index_max) > 0);

CREATE UNIQUE INDEX uq_fqs_tenant_iso_name
    ON production.prod_fiber_quality_standard (tenant_id, iso_code_id, standard_name)
    WHERE is_active = TRUE AND target_type = 'ISO_CODE';

CREATE UNIQUE INDEX uq_fqs_tenant_fiber_name
    ON production.prod_fiber_quality_standard (tenant_id, fiber_id, standard_name)
    WHERE is_active = TRUE AND target_type = 'FIBER';

-- Exactly one active default per tenant and target.
CREATE UNIQUE INDEX uq_fqs_tenant_iso_default
    ON production.prod_fiber_quality_standard (tenant_id, iso_code_id)
    WHERE is_active = TRUE AND is_default = TRUE AND target_type = 'ISO_CODE';

CREATE UNIQUE INDEX uq_fqs_tenant_fiber_default
    ON production.prod_fiber_quality_standard (tenant_id, fiber_id)
    WHERE is_active = TRUE AND is_default = TRUE AND target_type = 'FIBER';

CREATE INDEX idx_fiber_quality_standard_fiber
    ON production.prod_fiber_quality_standard (tenant_id, fiber_id, is_default);

COMMENT ON COLUMN production.prod_fiber_quality_standard.target_composition IS
    'FIBER target only: server-captured normalised composition {fiberId: percentage}; immutable.';
