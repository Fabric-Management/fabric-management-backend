-- Colour cards grow from (code, name, hex) into a shade standard:
--   * colour_type      disambiguates a NULL hex ("undyed") from "not filled in yet"
--   * pantone          external contractual reference (code + carrier system)
--   * target_lab_*     the TARGET colour, meaningless without illuminant + observer.
--                      Named "target_" so it can never be mistaken for a measured value:
--                      measured Lab belongs to a batch or a lab dip, never to the card.
--   * delta_e_*        acceptance tolerance, meaningless without its formula AND without
--                      a target to measure against (complete Lab, or a Pantone reference)
--   * standard_status  internal sign-off. APPROVED freezes the standard-defining fields.
--
-- No DB default on delta_e_formula: a default would silently populate the formula on cards
-- that carry no tolerance, breaking "formula without tolerance is rejected".

ALTER TABLE production.color
    ADD COLUMN IF NOT EXISTS color_type           VARCHAR(20)  NOT NULL DEFAULT 'DYED',
    ADD COLUMN IF NOT EXISTS color_family         VARCHAR(20)  NOT NULL DEFAULT 'UNDEFINED',
    ADD COLUMN IF NOT EXISTS pantone_code         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pantone_system       VARCHAR(10),
    ADD COLUMN IF NOT EXISTS standard_status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS target_lab_l         NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS target_lab_a         NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS target_lab_b         NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS target_lab_illuminant VARCHAR(10),
    ADD COLUMN IF NOT EXISTS target_lab_observer  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS delta_e_tolerance    NUMERIC(4,2),
    ADD COLUMN IF NOT EXISTS delta_e_formula      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS notes                VARCHAR(1000);

ALTER TABLE production.color
    DROP CONSTRAINT IF EXISTS chk_color_type,
    DROP CONSTRAINT IF EXISTS chk_color_family,
    DROP CONSTRAINT IF EXISTS chk_color_pantone_system,
    DROP CONSTRAINT IF EXISTS chk_color_pantone_system_needs_code,
    DROP CONSTRAINT IF EXISTS chk_color_standard_status,
    DROP CONSTRAINT IF EXISTS chk_color_target_lab_all_or_none,
    DROP CONSTRAINT IF EXISTS chk_color_target_lab_range,
    DROP CONSTRAINT IF EXISTS chk_color_delta_e_pair,
    DROP CONSTRAINT IF EXISTS chk_color_delta_e_needs_target,
    DROP CONSTRAINT IF EXISTS chk_color_undyed_has_no_standard;

ALTER TABLE production.color
    ADD CONSTRAINT chk_color_type
        CHECK (color_type IN ('DYED','YARN_DYED','PRINTED','OPTICAL_WHITE','PFD','GREIGE')),

    -- NOT NULL with an explicit UNDEFINED member: one way to say "not classified",
    -- never two (NULL vs UNDEFINED) that filters would have to special-case.
    ADD CONSTRAINT chk_color_family
        CHECK (color_family IN (
            'RED','ORANGE','YELLOW','GREEN','BLUE','PURPLE','PINK','BROWN',
            'BEIGE','GREY','BLACK','WHITE','MULTI','UNDEFINED')),

    ADD CONSTRAINT chk_color_pantone_system
        CHECK (pantone_system IS NULL OR pantone_system IN ('TCX','TPG','TPX','TN','TSX')),

    -- A carrier without a code says nothing.
    ADD CONSTRAINT chk_color_pantone_system_needs_code
        CHECK (pantone_system IS NULL OR pantone_code IS NOT NULL),

    ADD CONSTRAINT chk_color_standard_status
        CHECK (standard_status IN ('DRAFT','APPROVED')),

    -- Lab is a single measurement: all five parts, or none.
    ADD CONSTRAINT chk_color_target_lab_all_or_none
        CHECK (
            (target_lab_l IS NULL AND target_lab_a IS NULL AND target_lab_b IS NULL
             AND target_lab_illuminant IS NULL AND target_lab_observer IS NULL)
            OR
            (target_lab_l IS NOT NULL AND target_lab_a IS NOT NULL AND target_lab_b IS NOT NULL
             AND target_lab_illuminant IS NOT NULL AND target_lab_observer IS NOT NULL)
        ),

    ADD CONSTRAINT chk_color_target_lab_range
        CHECK (
            (target_lab_l IS NULL OR (target_lab_l >= 0 AND target_lab_l <= 100))
            AND (target_lab_a IS NULL OR (target_lab_a >= -128 AND target_lab_a <= 127))
            AND (target_lab_b IS NULL OR (target_lab_b >= -128 AND target_lab_b <= 127))
            AND (target_lab_illuminant IS NULL
                 OR target_lab_illuminant IN ('D65','D50','A','F11','TL84'))
            AND (target_lab_observer IS NULL OR target_lab_observer IN ('DEG_2','DEG_10'))
        ),

    -- A tolerance without its formula is not a number anyone can act on.
    ADD CONSTRAINT chk_color_delta_e_pair
        CHECK (
            (delta_e_tolerance IS NULL AND delta_e_formula IS NULL)
            OR
            (delta_e_tolerance IS NOT NULL AND delta_e_tolerance > 0
             AND delta_e_formula IN ('CIE76','CIE94','CIEDE2000','CMC_2_1'))
        ),

    -- ...and a tolerance with nothing to measure against is equally empty.
    ADD CONSTRAINT chk_color_delta_e_needs_target
        CHECK (
            delta_e_tolerance IS NULL
            OR target_lab_l IS NOT NULL
            OR pantone_code IS NOT NULL
        ),

    -- Undyed cloth has no shade standard to hold.
    ADD CONSTRAINT chk_color_undyed_has_no_standard
        CHECK (
            color_type NOT IN ('PFD','GREIGE')
            OR (color_hex IS NULL
                AND pantone_code IS NULL
                AND pantone_system IS NULL
                AND target_lab_l IS NULL
                AND delta_e_tolerance IS NULL)
        );

CREATE INDEX IF NOT EXISTS idx_color_tenant_family
    ON production.color (tenant_id, color_family);
