ALTER TABLE production.prod_fiber
    ADD COLUMN material_source VARCHAR(20);

ALTER TABLE production.production_fiber_request
    ADD COLUMN material_source VARCHAR(20);

ALTER TABLE production.prod_fiber
    ADD CONSTRAINT chk_fiber_material_source
        CHECK (material_source IS NULL OR material_source IN ('VIRGIN', 'RECYCLED')),
    ADD CONSTRAINT chk_fiber_material_source_pure_only
        CHECK (
            material_source IS NULL
            OR coalesce(composition, '{}'::jsonb) = '{}'::jsonb
        );

ALTER TABLE production.production_fiber_request
    ADD CONSTRAINT chk_fiber_request_material_source
        CHECK (material_source IS NULL OR material_source IN ('VIRGIN', 'RECYCLED'));

DO $$
DECLARE
    duplicate_keys TEXT;
BEGIN
    SELECT string_agg(
               format(
                   '(tenant=%s, iso_id=%s, source=%s, count=%s)',
                   tenant_id,
                   fiber_iso_code_id,
                   material_source_key,
                   row_count
               ),
               ', '
           )
      INTO duplicate_keys
      FROM (
          SELECT tenant_id,
                 fiber_iso_code_id,
                 coalesce(material_source, 'UNDECLARED') AS material_source_key,
                 count(*) AS row_count
            FROM production.prod_fiber
           WHERE is_active = TRUE
             AND coalesce(composition, '{}'::jsonb) = '{}'::jsonb
           GROUP BY tenant_id,
                    fiber_iso_code_id,
                    coalesce(material_source, 'UNDECLARED')
          HAVING count(*) > 1
      ) duplicates;

    IF duplicate_keys IS NOT NULL THEN
        RAISE EXCEPTION
            'FIBER-SRC-1 preflight failed: duplicate active pure fiber variants: %',
            duplicate_keys;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_keys TEXT;
BEGIN
    SELECT string_agg(
               format(
                   '(tenant=%s, iso=%s, source=%s, count=%s)',
                   tenant_id,
                   normalized_iso_code,
                   material_source_key,
                   row_count
               ),
               ', '
           )
      INTO duplicate_keys
      FROM (
          SELECT tenant_id,
                 upper(iso_code) AS normalized_iso_code,
                 coalesce(material_source, 'UNDECLARED') AS material_source_key,
                 count(*) AS row_count
            FROM production.production_fiber_request
           WHERE status IN ('PENDING', 'APPROVED')
           GROUP BY tenant_id,
                    upper(iso_code),
                    coalesce(material_source, 'UNDECLARED')
          HAVING count(*) > 1
      ) duplicates;

    IF duplicate_keys IS NOT NULL THEN
        RAISE EXCEPTION
            'FIBER-SRC-1 preflight failed: duplicate open fiber requests: %',
            duplicate_keys;
    END IF;
END $$;

CREATE UNIQUE INDEX uq_fiber_request_open_iso_source
    ON production.production_fiber_request (
        tenant_id,
        upper(iso_code),
        coalesce(material_source, 'UNDECLARED')
    )
    WHERE status IN ('PENDING', 'APPROVED');

CREATE UNIQUE INDEX uq_fiber_active_pure_iso_source
    ON production.prod_fiber (
        tenant_id,
        fiber_iso_code_id,
        coalesce(material_source, 'UNDECLARED')
    )
    WHERE is_active = TRUE
      AND coalesce(composition, '{}'::jsonb) = '{}'::jsonb;
