-- BE-3 / ADR-0010: hard cutover of batch colour from EAV attributes to batch.color_id.
-- This migration is intentionally transactional. Do not split it or mark it non-transactional.

DO $$
DECLARE
    has_color_column BOOLEAN;
    offending_batch_ids UUID[];
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'production'
          AND table_name = 'production_execution_batch'
          AND column_name = 'color_id'
    ) INTO has_color_column;

    IF has_color_column THEN
        EXECUTE $sql$
            SELECT array_agg(id)
            FROM (
                SELECT id
                FROM production.production_execution_batch
                WHERE color_id IS NOT NULL
                ORDER BY id
                LIMIT 20
            ) offending
        $sql$ INTO offending_batch_ids;

        IF offending_batch_ids IS NOT NULL THEN
            RAISE EXCEPTION
                'BE-3 preflight #6: pre-existing non-NULL batch.color_id values found; offending batch ids: %',
                offending_batch_ids;
        END IF;
    END IF;
END $$;

ALTER TABLE production.production_execution_batch
    ADD COLUMN IF NOT EXISTS color_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_exec_batch_tenant_color'
          AND conrelid = 'production.production_execution_batch'::regclass
    ) THEN
        ALTER TABLE production.production_execution_batch
            ADD CONSTRAINT fk_exec_batch_tenant_color
            FOREIGN KEY (tenant_id, color_id)
            REFERENCES production.color (tenant_id, id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_exec_batch_tenant_color
    ON production.production_execution_batch (tenant_id, color_id);

CREATE INDEX IF NOT EXISTS idx_exec_batch_tenant_product_color
    ON production.production_execution_batch (tenant_id, product_id, color_id);

CREATE TABLE IF NOT EXISTS production.production_execution_batch_color_archive (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_row_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    attribute_definition_id UUID NOT NULL,
    attribute_code VARCHAR(50) NOT NULL,
    attribute_value TEXT,
    resolved_color_id UUID NOT NULL,
    prev_is_active BOOLEAN NOT NULL,
    prev_deleted_at TIMESTAMPTZ,
    prev_created_at TIMESTAMP NOT NULL,
    prev_created_by UUID,
    prev_updated_at TIMESTAMP NOT NULL,
    prev_updated_by UUID,
    prev_version BIGINT NOT NULL,
    cutover_version VARCHAR(50) NOT NULL,
    cutover_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_batch_color_archive_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_batch_color_archive_source
        UNIQUE (tenant_id, source_row_id)
);

CREATE INDEX idx_batch_color_archive_tenant
    ON production.production_execution_batch_color_archive (tenant_id);

ALTER TABLE production.production_execution_batch_color_archive ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch_color_archive FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation
    ON production.production_execution_batch_color_archive
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        REVOKE INSERT, UPDATE, DELETE
            ON production.production_execution_batch_color_archive FROM fabric_app;
        GRANT SELECT
            ON production.production_execution_batch_color_archive TO fabric_app;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        REVOKE INSERT, UPDATE
            ON production.production_execution_batch_color_archive FROM fabric_system;
        GRANT SELECT, DELETE
            ON production.production_execution_batch_color_archive TO fabric_system;
    END IF;
END $$;

-- #1: every tenant boundary in the source chain must agree. A UUID that names another
-- tenant's color is a tenant mismatch, not an ordinary unresolved UUID.
DO $$
DECLARE
    offending_source_ids UUID[];
BEGIN
    SELECT array_agg(source_row_id)
    INTO offending_source_ids
    FROM (
        SELECT ba.id AS source_row_id
        FROM production.production_execution_batch_attribute ba
        JOIN production.production_execution_batch b ON b.id = ba.batch_id
        JOIN production.prod_product_attribute pa ON pa.id = ba.attribute_id
        LEFT JOIN production.color foreign_color
          ON CASE
               WHEN btrim(ba.value) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
               THEN foreign_color.id = btrim(ba.value)::uuid
               ELSE FALSE
             END
         AND foreign_color.tenant_id <> ba.tenant_id
        WHERE ba.is_active = TRUE
          AND pa.is_active = TRUE
          AND upper(btrim(pa.attribute_code)) IN
              ('COLOR', 'COLOUR', 'COLOR_ID', 'COLOUR_ID', 'SHADE')
          AND (
              ba.tenant_id <> b.tenant_id
              OR ba.tenant_id <> pa.tenant_id
              OR foreign_color.id IS NOT NULL
          )
        ORDER BY ba.id
        LIMIT 20
    ) offending;

    IF offending_source_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'BE-3 preflight #1: tenant mismatch in legacy color chain; offending source row ids: %',
            offending_source_ids;
    END IF;
END $$;

-- #2: a recognised active attribute must carry a non-blank value.
DO $$
DECLARE
    offending_source_ids UUID[];
BEGIN
    SELECT array_agg(source_row_id)
    INTO offending_source_ids
    FROM (
        SELECT ba.id AS source_row_id
        FROM production.production_execution_batch_attribute ba
        JOIN production.prod_product_attribute pa ON pa.id = ba.attribute_id
        WHERE ba.is_active = TRUE
          AND pa.is_active = TRUE
          AND upper(btrim(pa.attribute_code)) IN
              ('COLOR', 'COLOUR', 'COLOR_ID', 'COLOUR_ID', 'SHADE')
          AND (ba.value IS NULL OR btrim(ba.value) = '')
        ORDER BY ba.id
        LIMIT 20
    ) offending;

    IF offending_source_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'BE-3 preflight #2: NULL or blank legacy color value; offending source row ids: %',
            offending_source_ids;
    END IF;
END $$;

CREATE TEMP TABLE batch_color_cutover_resolution ON COMMIT DROP AS
SELECT
    ba.id AS source_row_id,
    ba.tenant_id,
    ba.batch_id,
    ba.attribute_id AS attribute_definition_id,
    pa.attribute_code,
    ba.value AS attribute_value,
    ba.is_active AS prev_is_active,
    ba.deleted_at AS prev_deleted_at,
    ba.created_at AS prev_created_at,
    ba.created_by AS prev_created_by,
    ba.updated_at AS prev_updated_at,
    ba.updated_by AS prev_updated_by,
    ba.version AS prev_version,
    btrim(ba.value) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AS uuid_shaped,
    CASE
        WHEN btrim(ba.value) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        THEN (
            SELECT count(*)
            FROM production.color c
            WHERE c.tenant_id = ba.tenant_id
              AND c.id = btrim(ba.value)::uuid
        )
        ELSE 0
    END AS uuid_match_count,
    CASE
        WHEN btrim(ba.value) !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        THEN (
            SELECT count(*)
            FROM production.color c
            WHERE c.tenant_id = ba.tenant_id
              AND c.code = upper(btrim(ba.value))
        )
        ELSE 0
    END AS code_match_count,
    CASE
        WHEN btrim(ba.value) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        THEN (
            SELECT c.id
            FROM production.color c
            WHERE c.tenant_id = ba.tenant_id
              AND c.id = btrim(ba.value)::uuid
        )
        ELSE (
            SELECT c.id
            FROM production.color c
            WHERE c.tenant_id = ba.tenant_id
              AND c.code = upper(btrim(ba.value))
        )
    END AS resolved_color_id
FROM production.production_execution_batch_attribute ba
JOIN production.production_execution_batch b ON b.id = ba.batch_id
JOIN production.prod_product_attribute pa ON pa.id = ba.attribute_id
WHERE ba.is_active = TRUE
  AND pa.is_active = TRUE
  AND upper(btrim(pa.attribute_code)) IN
      ('COLOR', 'COLOUR', 'COLOR_ID', 'COLOUR_ID', 'SHADE');

-- #3: once a value is UUID-shaped, canonical code fallback is forbidden.
DO $$
DECLARE
    offending_source_ids UUID[];
BEGIN
    SELECT array_agg(source_row_id)
    INTO offending_source_ids
    FROM (
        SELECT source_row_id
        FROM batch_color_cutover_resolution
        WHERE uuid_shaped = TRUE
          AND uuid_match_count <> 1
        ORDER BY source_row_id
        LIMIT 20
    ) offending;

    IF offending_source_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'BE-3 preflight #3: UUID legacy color value did not resolve exactly once; offending source row ids: %',
            offending_source_ids;
    END IF;
END $$;

-- #4: non-UUID values resolve only through the tenant's canonical color.code.
DO $$
DECLARE
    offending_source_ids UUID[];
BEGIN
    SELECT array_agg(source_row_id)
    INTO offending_source_ids
    FROM (
        SELECT source_row_id
        FROM batch_color_cutover_resolution
        WHERE uuid_shaped = FALSE
          AND code_match_count <> 1
        ORDER BY source_row_id
        LIMIT 20
    ) offending;

    IF offending_source_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'BE-3 preflight #4: canonical color code did not resolve exactly once; offending source row ids: %',
            offending_source_ids;
    END IF;
END $$;

-- #5: multiple recognised attributes are allowed only when they agree on one card.
DO $$
DECLARE
    offending_batch_ids UUID[];
BEGIN
    SELECT array_agg(batch_id)
    INTO offending_batch_ids
    FROM (
        SELECT batch_id
        FROM batch_color_cutover_resolution
        GROUP BY batch_id
        HAVING count(DISTINCT resolved_color_id) > 1
        ORDER BY batch_id
        LIMIT 20
    ) offending;

    IF offending_batch_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'BE-3 preflight #5: legacy color attributes disagree; offending batch ids: %',
            offending_batch_ids;
    END IF;
END $$;

UPDATE production.production_execution_batch b
SET color_id = resolved.resolved_color_id
FROM (
    SELECT batch_id, (array_agg(resolved_color_id))[1] AS resolved_color_id
    FROM batch_color_cutover_resolution
    GROUP BY batch_id
) resolved
WHERE b.id = resolved.batch_id;

-- #7: defensive terminal parity after the backfill.
DO $$
DECLARE
    source_count BIGINT;
    resolved_count BIGINT;
    parity_count BIGINT;
BEGIN
    SELECT count(*), count(resolved_color_id)
    INTO source_count, resolved_count
    FROM batch_color_cutover_resolution;

    SELECT count(*)
    INTO parity_count
    FROM batch_color_cutover_resolution r
    JOIN production.production_execution_batch b
      ON b.id = r.batch_id
     AND b.tenant_id = r.tenant_id
     AND b.color_id = r.resolved_color_id;

    IF source_count <> resolved_count OR source_count <> parity_count THEN
        RAISE EXCEPTION
            'BE-3 preflight #7: terminal parity failed (source=%, resolved=%, batch-parity=%)',
            source_count, resolved_count, parity_count;
    END IF;
END $$;

INSERT INTO production.production_execution_batch_color_archive (
    tenant_id,
    source_row_id,
    batch_id,
    attribute_definition_id,
    attribute_code,
    attribute_value,
    resolved_color_id,
    prev_is_active,
    prev_deleted_at,
    prev_created_at,
    prev_created_by,
    prev_updated_at,
    prev_updated_by,
    prev_version,
    cutover_version,
    cutover_at
)
SELECT
    tenant_id,
    source_row_id,
    batch_id,
    attribute_definition_id,
    attribute_code,
    attribute_value,
    resolved_color_id,
    prev_is_active,
    prev_deleted_at,
    prev_created_at,
    prev_created_by,
    prev_updated_at,
    prev_updated_by,
    prev_version,
    'V20260718120000',
    CURRENT_TIMESTAMP
FROM batch_color_cutover_resolution;

UPDATE production.production_execution_batch_attribute ba
SET is_active = FALSE,
    deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    version = ba.version + 1
FROM batch_color_cutover_resolution resolved
WHERE ba.id = resolved.source_row_id
  AND ba.tenant_id = resolved.tenant_id;
