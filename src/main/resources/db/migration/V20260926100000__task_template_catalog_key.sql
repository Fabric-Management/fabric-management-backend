-- TASK-TEMPLATE-TENANCY-1 (M1): stable catalogue identity for task templates.
-- NULL = tenant-authored. A non-null key marks a row that belongs to the platform catalogue
-- (golden-template) or is a tenant's copy of it. Uniqueness is per tenant, keyed rows only.
SET LOCAL lock_timeout = '5s';

ALTER TABLE flowboard.task_template
    ADD COLUMN IF NOT EXISTS catalog_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_template_tenant_catalog_key
    ON flowboard.task_template (tenant_id, catalog_key)
    WHERE catalog_key IS NOT NULL;
