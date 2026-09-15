ALTER TABLE flowboard.task
    ADD COLUMN generation_key VARCHAR(500),
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN workflow_definition_id UUID,
    ADD COLUMN workflow_version INTEGER;

ALTER TABLE flowboard.task
    DROP CONSTRAINT IF EXISTS chk_task_type,
    ADD CONSTRAINT chk_task_type CHECK (task_type IN (
        'PLANNING','PRODUCTION','QUALITY','WAREHOUSE','SHIPMENT',
        'APPROVAL','RECIPE_ASSIGNMENT','PROCUREMENT','COSTING',
        'SAMPLE','RETURN','STOCK_COUNT','MAINTENANCE','ORDER_COVER','GENERAL'
    ));

UPDATE flowboard.task
SET generation_key = CASE
    WHEN status IN ('DONE', 'CANCELLED') THEN 'task:legacy-terminal:' || id
    WHEN source_type = 'MANUAL' THEN 'task:legacyManual:' || id
    WHEN source_type = 'TEMPLATE' AND entity_type IS NOT NULL AND entity_id IS NOT NULL
        THEN 'subjectType:' || UPPER(entity_type) || ':subjectId:' || entity_id
             || ':taskType:' || task_type || ':fulfillmentMode:NONE'
    WHEN source_type = 'AUTOMATION_RULE' AND source_id IS NOT NULL
         AND entity_type IS NOT NULL AND entity_id IS NOT NULL
        THEN 'automationRule:' || source_id || ':subjectType:' || UPPER(entity_type)
             || ':subjectId:' || entity_id || ':taskType:' || task_type
             || ':fulfillmentMode:NONE'
    -- Subject-less legacy automation does not retain the triggering Task identity needed by the
    -- runtime key. Keep an explicit per-row legacy identity instead of inventing that subject.
    ELSE 'task:legacy:' || id
END,
closed_at = CASE
    -- Older rows did not record cancellation time. updated_at is an explicitly labelled
    -- approximation when the true terminal timestamp is unavailable; it is never presented as
    -- completed_at domain truth.
    WHEN status IN ('DONE', 'CANCELLED') THEN COALESCE(completed_at, updated_at)
    ELSE NULL
END,
workflow_definition_id = 'a9ce2df6-f274-4f61-8e53-d28f27472b01'::UUID,
workflow_version = 1;

DO $$
DECLARE
    collision_keys TEXT;
BEGIN
    SELECT string_agg(tenant_id || '/' || generation_key || ' (' || row_count || ')', ', ')
    INTO collision_keys
    FROM (
        SELECT tenant_id, generation_key, count(*) AS row_count
        FROM flowboard.task
        WHERE is_active = TRUE AND closed_at IS NULL
        GROUP BY tenant_id, generation_key
        HAVING count(*) > 1
        ORDER BY tenant_id, generation_key
        LIMIT 50
    ) collisions;

    IF collision_keys IS NOT NULL THEN
        RAISE EXCEPTION
            'FE-ARCH-5b-1 active Task generation-key collisions require review: %',
            collision_keys;
    END IF;
END $$;

ALTER TABLE flowboard.task
    ALTER COLUMN generation_key SET NOT NULL,
    ALTER COLUMN workflow_definition_id SET NOT NULL,
    ALTER COLUMN workflow_version SET NOT NULL,
    ADD CONSTRAINT uq_task_id_tenant UNIQUE (id, tenant_id),
    ADD CONSTRAINT chk_task_workflow_pin
        CHECK ((workflow_definition_id IS NULL) = (workflow_version IS NULL)),
    ADD CONSTRAINT chk_task_workflow_version
        CHECK (workflow_version IS NULL OR workflow_version > 0);

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_active_generation_key
    ON flowboard.task (tenant_id, generation_key)
    WHERE is_active = TRUE AND closed_at IS NULL;

CREATE TABLE IF NOT EXISTS flowboard.task_affected_subject (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    task_id UUID NOT NULL,
    subject_type VARCHAR(80) NOT NULL,
    subject_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_task_affected_subject UNIQUE (tenant_id, task_id, subject_type, subject_id),
    CONSTRAINT fk_task_affected_subject_task
        FOREIGN KEY (task_id, tenant_id) REFERENCES flowboard.task(id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_task_affected_subject_lookup
    ON flowboard.task_affected_subject (tenant_id, subject_type, subject_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS flowboard.task_transition_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    task_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    action_key VARCHAR(80) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    result_type VARCHAR(80),
    result_id UUID,
    rejection_code VARCHAR(80),
    rejection_message VARCHAR(500),
    completed_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_task_transition_attempt UNIQUE (tenant_id, actor_id, idempotency_key),
    CONSTRAINT fk_task_transition_attempt_task
        FOREIGN KEY (task_id, tenant_id) REFERENCES flowboard.task(id, tenant_id),
    CONSTRAINT chk_task_transition_outcome
        CHECK (outcome IN ('PENDING', 'ACCEPTED', 'REJECTED_BUSINESS')),
    CONSTRAINT chk_task_transition_result CHECK (
        (outcome = 'PENDING' AND completed_at IS NULL AND result_type IS NULL AND result_id IS NULL
            AND rejection_code IS NULL AND rejection_message IS NULL)
        OR
        (outcome = 'ACCEPTED' AND completed_at IS NOT NULL
            AND result_type IS NOT NULL AND result_id IS NOT NULL
            AND rejection_code IS NULL AND rejection_message IS NULL)
        OR
        (outcome = 'REJECTED_BUSINESS' AND completed_at IS NOT NULL
            AND result_type IS NULL AND result_id IS NULL
            AND rejection_code IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_task_transition_attempt_task
    ON flowboard.task_transition_attempt (tenant_id, task_id, completed_at DESC);

ALTER TABLE flowboard.task_affected_subject ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_affected_subject FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.task_affected_subject
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

ALTER TABLE flowboard.task_transition_attempt ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_transition_attempt FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.task_transition_attempt
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
        flowboard.task_affected_subject,
        flowboard.task_transition_attempt
    TO fabric_app;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
        flowboard.task_affected_subject,
        flowboard.task_transition_attempt
    TO fabric_system;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;
