CREATE TABLE IF NOT EXISTS sales.quote_send_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    quote_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_quote_send_request_tenant
        FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quote_send_request_quote
        FOREIGN KEY (quote_id) REFERENCES sales.quote(id) ON DELETE CASCADE,
    CONSTRAINT fk_quote_send_request_contact
        FOREIGN KEY (contact_id) REFERENCES common_company.partner_contact(id) ON DELETE RESTRICT,
    CONSTRAINT ck_quote_send_request_channel
        CHECK (channel IN ('EMAIL', 'WHATSAPP', 'IN_PERSON')),
    CONSTRAINT ck_quote_send_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_quote_send_request_reject_note
        CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_quote_send_request_tenant
    ON sales.quote_send_request(tenant_id);
CREATE INDEX IF NOT EXISTS idx_quote_send_request_quote
    ON sales.quote_send_request(quote_id);
CREATE INDEX IF NOT EXISTS idx_quote_send_request_contact
    ON sales.quote_send_request(contact_id);
CREATE INDEX IF NOT EXISTS idx_quote_send_request_requested_by
    ON sales.quote_send_request(requested_by);
CREATE UNIQUE INDEX IF NOT EXISTS uq_quote_send_request_open_quote
    ON sales.quote_send_request(tenant_id, quote_id)
    WHERE status = 'PENDING' AND is_active = TRUE;

ALTER TABLE sales.quote_send_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.quote_send_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.quote_send_request;
CREATE POLICY rls_tenant_isolation ON sales.quote_send_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

INSERT INTO flowboard.task_template (
    id, tenant_id, uid, name, event_type, title_template, task_type,
    module_type, default_priority, default_assignee_role, estimated_hours,
    is_active, created_at, updated_at, version
)
SELECT
    gen_random_uuid(),
    tenant_ids.tenant_id,
    gen_random_uuid()::varchar,
    'Quote send approval',
    'QuoteSendRequested',
    'Quote send approval - {quote.quoteNumber}',
    'APPROVAL',
    'GENERAL',
    'HIGH',
    'ANY',
    0.25,
    TRUE,
    NOW(),
    NOW(),
    0
FROM (
    SELECT DISTINCT tenant_id FROM flowboard.task_template
    UNION
    SELECT '00000000-0000-0000-ffff-000000000001'::uuid
) tenant_ids
WHERE NOT EXISTS (
    SELECT 1
    FROM flowboard.task_template existing
    WHERE existing.tenant_id = tenant_ids.tenant_id
      AND existing.event_type = 'QuoteSendRequested'
      AND existing.task_type = 'APPROVAL'
      AND existing.is_active = TRUE
);

INSERT INTO common_user.permission_template (
    id, tenant_id, uid, role_code, department_code, resource, action,
    data_scope, is_active, created_at, updated_at, version
)
SELECT
    gen_random_uuid(),
    src.tenant_id,
    gen_random_uuid()::varchar,
    src.role_code,
    src.department_code,
    'sales',
    'approve',
    CASE WHEN src.role_code IN ('ADMIN', 'PLATFORM_ADMIN') THEN 'GLOBAL' ELSE 'ORGANIZATION' END,
    TRUE,
    NOW(),
    NOW(),
    0
FROM (
    SELECT DISTINCT tenant_id, role_code, department_code
    FROM common_user.permission_template
    WHERE role_code IN ('ADMIN', 'PLATFORM_ADMIN', 'MANAGER', 'SUPERVISOR')
      AND tenant_id IS NOT NULL
    UNION
    SELECT '00000000-0000-0000-ffff-000000000001'::uuid, 'ADMIN', NULL
    UNION
    SELECT '00000000-0000-0000-ffff-000000000001'::uuid, 'MANAGER', NULL
    UNION
    SELECT '00000000-0000-0000-ffff-000000000001'::uuid, 'SUPERVISOR', NULL
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM common_user.permission_template existing
    WHERE existing.tenant_id = src.tenant_id
      AND existing.role_code = src.role_code
      AND COALESCE(existing.department_code, '__ALL__') = COALESCE(src.department_code, '__ALL__')
      AND existing.resource = 'sales'
      AND existing.action = 'approve'
);
