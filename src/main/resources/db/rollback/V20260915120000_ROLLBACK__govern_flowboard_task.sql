DROP TABLE IF EXISTS flowboard.task_transition_attempt;
DROP TABLE IF EXISTS flowboard.task_affected_subject;
DROP INDEX IF EXISTS flowboard.uq_task_active_generation_key;
ALTER TABLE flowboard.task
    DROP CONSTRAINT IF EXISTS uq_task_id_tenant,
    DROP CONSTRAINT IF EXISTS chk_task_workflow_version,
    DROP CONSTRAINT IF EXISTS chk_task_workflow_pin,
    DROP COLUMN IF EXISTS workflow_version,
    DROP COLUMN IF EXISTS workflow_definition_id,
    DROP COLUMN IF EXISTS closed_at,
    DROP COLUMN IF EXISTS generation_key;

ALTER TABLE flowboard.task
    DROP CONSTRAINT IF EXISTS chk_task_type,
    ADD CONSTRAINT chk_task_type CHECK (task_type IN (
        'PLANNING','PRODUCTION','QUALITY','WAREHOUSE','SHIPMENT',
        'APPROVAL','RECIPE_ASSIGNMENT','PROCUREMENT','COSTING',
        'SAMPLE','RETURN','STOCK_COUNT','MAINTENANCE','GENERAL'
    ));
