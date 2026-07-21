ALTER TABLE production.production_execution_batch
    ADD CONSTRAINT uq_batch_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE production.stock_unit
    ADD CONSTRAINT uq_stock_unit_id_tenant UNIQUE (id, tenant_id);

-- The decision service locks the batch before freezing its StockUnit population. This tenant-safe
-- FK makes concurrent StockUnit inserts take a key-share lock on that same batch row, so they
-- cannot slip in between the population snapshot and the compatibility projection.
ALTER TABLE production.stock_unit
    ADD CONSTRAINT fk_stock_unit_batch_tenant
        FOREIGN KEY (batch_id, tenant_id)
        REFERENCES production.production_execution_batch (id, tenant_id)
        ON DELETE RESTRICT;

ALTER TABLE production.stock_unit
    ADD COLUMN quality_disposition VARCHAR(30);

UPDATE production.stock_unit su
SET quality_disposition = CASE
    WHEN su.status = 'QUARANTINE' THEN 'QUARANTINED'
    WHEN batch.status = 'PENDING_QC' THEN 'PENDING_INSPECTION'
    WHEN batch.status = 'QUARANTINE' THEN 'QUARANTINED'
    WHEN batch.status = 'QC_REJECTED' THEN 'NONCONFORMING'
    WHEN batch.status IN ('AVAILABLE', 'RESERVED', 'IN_PROGRESS', 'ON_HOLD', 'DEPLETED')
        THEN 'RELEASED'
    WHEN batch.status IN ('RETURNED', 'DESTROYED') THEN 'NONCONFORMING'
END
FROM production.production_execution_batch batch
WHERE batch.id = su.batch_id
  AND batch.tenant_id = su.tenant_id;

DO $$
DECLARE
    legacy_quarantine_count BIGINT;
    legacy_quarantine_ids UUID[];
BEGIN
    SELECT count(*)
    INTO legacy_quarantine_count
    FROM production.stock_unit
    WHERE status = 'QUARANTINE';

    SELECT array_agg(id ORDER BY id)
    INTO legacy_quarantine_ids
    FROM (
        SELECT id
        FROM production.stock_unit
        WHERE status = 'QUARANTINE'
        ORDER BY id
        LIMIT 100
    ) legacy;

    IF legacy_quarantine_count > 0 THEN
        RAISE NOTICE
            'QC-RELEASE-1a retained % legacy QUARANTINE StockUnits for manual operational-status review; first ids: %',
            legacy_quarantine_count,
            legacy_quarantine_ids;
    END IF;
END $$;

DO $$
DECLARE
    unmapped_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO unmapped_count
    FROM production.stock_unit
    WHERE quality_disposition IS NULL;

    IF unmapped_count > 0 THEN
        RAISE EXCEPTION
            'QC-RELEASE-1a cannot map % stock_unit rows to quality_disposition; manual review required',
            unmapped_count;
    END IF;
END $$;

ALTER TABLE production.stock_unit
    ALTER COLUMN quality_disposition SET NOT NULL,
    ADD CONSTRAINT chk_stock_unit_quality_disposition
        CHECK (quality_disposition IN (
            'PENDING_INSPECTION', 'RELEASED', 'QUARANTINED', 'NONCONFORMING'
        ));

CREATE TABLE IF NOT EXISTS production.quality_decision (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    batch_id                 UUID NOT NULL,
    decision_scope           VARCHAR(30) NOT NULL,
    outcome                  VARCHAR(30) NOT NULL,
    reason_code              VARCHAR(50),
    remarks                  TEXT,
    actor_id                 UUID NOT NULL,
    origin                   VARCHAR(40) NOT NULL,
    source_event_id          UUID,
    supersedes_decision_id   UUID,
    decided_at               TIMESTAMPTZ NOT NULL,
    seq                      BIGINT NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_quality_decision_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_quality_decision_batch_seq UNIQUE (tenant_id, batch_id, seq),
    CONSTRAINT uq_quality_decision_source_event UNIQUE (tenant_id, source_event_id),
    CONSTRAINT fk_quality_decision_batch
        FOREIGN KEY (batch_id, tenant_id)
        REFERENCES production.production_execution_batch (id, tenant_id),
    CONSTRAINT fk_quality_decision_supersedes
        FOREIGN KEY (supersedes_decision_id, tenant_id)
        REFERENCES production.quality_decision (id, tenant_id),
    CONSTRAINT chk_quality_decision_scope
        CHECK (decision_scope IN ('FULL_LOT', 'SELECTED_UNITS')),
    CONSTRAINT chk_quality_decision_outcome
        CHECK (outcome IN ('RELEASED', 'QUARANTINED', 'NONCONFORMING')),
    CONSTRAINT chk_quality_decision_origin
        CHECK (origin IN (
            'MANUAL', 'SYSTEM_RELEASE', 'SYSTEM_QC_EVENT', 'MIGRATION_BACKFILL'
        )),
    CONSTRAINT chk_quality_decision_source_event
        CHECK (
            (origin = 'SYSTEM_QC_EVENT' AND source_event_id IS NOT NULL)
            OR (origin <> 'SYSTEM_QC_EVENT' AND source_event_id IS NULL)
        ),
    CONSTRAINT chk_quality_decision_reason_matrix
        CHECK (
            (origin = 'MANUAL' AND outcome = 'RELEASED' AND reason_code IS NULL)
            OR (origin = 'MANUAL' AND outcome = 'QUARANTINED'
                AND reason_code IN (
                    'SUSPECTED_DAMAGE', 'AWAITING_LAB', 'SUPPLIER_DISPUTE',
                    'SHADE_CHECK', 'OTHER'
                ))
            OR (origin = 'MANUAL' AND outcome = 'NONCONFORMING'
                AND reason_code IN (
                    'DAMAGE', 'STAIN', 'SHADE_VARIATION', 'SHORT_LENGTH',
                    'MEASURE_MISMATCH', 'OTHER'
                ))
            OR (origin = 'SYSTEM_RELEASE' AND outcome = 'RELEASED'
                AND reason_code = 'SYSTEM_QC_PASSED')
            OR (origin = 'SYSTEM_QC_EVENT' AND outcome = 'RELEASED'
                AND reason_code = 'SYSTEM_QC_PASSED')
            OR (origin = 'SYSTEM_QC_EVENT' AND outcome = 'NONCONFORMING'
                AND reason_code = 'SYSTEM_QC_REJECTED')
            OR (origin = 'MIGRATION_BACKFILL' AND reason_code = 'MIGRATION_BASELINE')
        ),
    CONSTRAINT chk_quality_decision_other_remarks
        CHECK (reason_code <> 'OTHER' OR NULLIF(BTRIM(remarks), '') IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS production.quality_decision_unit (
    tenant_id       UUID NOT NULL,
    decision_id     UUID NOT NULL,
    stock_unit_id   UUID NOT NULL,

    CONSTRAINT pk_quality_decision_unit
        PRIMARY KEY (tenant_id, decision_id, stock_unit_id),
    CONSTRAINT fk_quality_decision_unit_decision
        FOREIGN KEY (decision_id, tenant_id)
        REFERENCES production.quality_decision (id, tenant_id),
    CONSTRAINT fk_quality_decision_unit_stock_unit
        FOREIGN KEY (stock_unit_id, tenant_id)
        REFERENCES production.stock_unit (id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_quality_decision_history
    ON production.quality_decision (tenant_id, batch_id, decided_at DESC, seq DESC);

CREATE INDEX IF NOT EXISTS idx_quality_decision_unit_stock
    ON production.quality_decision_unit (tenant_id, stock_unit_id);

CREATE INDEX IF NOT EXISTS idx_stock_unit_pending_quality
    ON production.stock_unit (tenant_id, quality_disposition, batch_id)
    WHERE is_active = TRUE;

CREATE TEMP TABLE IF NOT EXISTS qc_release_baseline ON COMMIT DROP AS
SELECT
    gen_random_uuid() AS decision_id,
    su.tenant_id,
    su.batch_id,
    su.quality_disposition AS outcome,
    CASE
        WHEN COUNT(*) = (
            SELECT COUNT(*)
            FROM production.stock_unit population
            WHERE population.tenant_id = su.tenant_id
              AND population.batch_id = su.batch_id
        ) THEN 'FULL_LOT'
        ELSE 'SELECTED_UNITS'
    END AS decision_scope,
    ROW_NUMBER() OVER (
        PARTITION BY su.tenant_id, su.batch_id
        ORDER BY su.quality_disposition
    ) AS seq
FROM production.stock_unit su
WHERE su.quality_disposition <> 'PENDING_INSPECTION'
GROUP BY su.tenant_id, su.batch_id, su.quality_disposition;

INSERT INTO production.quality_decision (
    id, tenant_id, batch_id, decision_scope, outcome, reason_code, remarks,
    actor_id, origin, source_event_id, supersedes_decision_id, decided_at, seq, created_at
)
SELECT
    decision_id,
    tenant_id,
    batch_id,
    decision_scope,
    outcome,
    'MIGRATION_BASELINE',
    'QC-RELEASE-1a baseline generated from the pre-cutover batch/unit state',
    '00000000-0000-0000-0000-000000000001'::UUID,
    'MIGRATION_BACKFILL',
    NULL,
    NULL,
    clock_timestamp(),
    seq,
    clock_timestamp()
FROM qc_release_baseline;

INSERT INTO production.quality_decision_unit (tenant_id, decision_id, stock_unit_id)
SELECT baseline.tenant_id, baseline.decision_id, unit.id
FROM qc_release_baseline baseline
JOIN production.stock_unit unit
 ON unit.tenant_id = baseline.tenant_id
 AND unit.batch_id = baseline.batch_id
 AND unit.quality_disposition = baseline.outcome;

CREATE OR REPLACE FUNCTION production.reject_quality_decision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_quality_decision_append_only
    BEFORE UPDATE OR DELETE ON production.quality_decision
    FOR EACH ROW EXECUTE FUNCTION production.reject_quality_decision_mutation();

CREATE TRIGGER trg_quality_decision_unit_append_only
    BEFORE UPDATE OR DELETE ON production.quality_decision_unit
    FOR EACH ROW EXECUTE FUNCTION production.reject_quality_decision_mutation();

ALTER TABLE production.quality_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.quality_decision FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.quality_decision
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

ALTER TABLE production.quality_decision_unit ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.quality_decision_unit FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.quality_decision_unit
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

DO $$
BEGIN
    GRANT SELECT, INSERT ON TABLE
        production.quality_decision,
        production.quality_decision_unit
    TO fabric_app;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

DO $$
BEGIN
    GRANT SELECT, INSERT ON TABLE
        production.quality_decision,
        production.quality_decision_unit
    TO fabric_system;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;
