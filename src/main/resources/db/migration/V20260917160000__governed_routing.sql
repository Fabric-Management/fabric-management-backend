-- FE-ARCH-5b-1b: governed routing. No pool or permission grants are seeded.
SET LOCAL lock_timeout = '5s';
CREATE UNIQUE INDEX IF NOT EXISTS uq_routing_task_tenant_id ON flowboard.task (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_routing_user_tenant_id ON common_user.common_user (tenant_id, id);

CREATE TABLE IF NOT EXISTS flowboard.routing_pool (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    pool_key VARCHAR(30) NOT NULL CHECK (pool_key = 'ORDER_COVER'),
    revision BIGINT NOT NULL CHECK (revision > 0),
    UNIQUE (tenant_id, pool_key), UNIQUE (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS flowboard.routing_pool_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    pool_id UUID NOT NULL, user_id UUID NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (tenant_id, pool_id, user_id),
    FOREIGN KEY (tenant_id, pool_id) REFERENCES flowboard.routing_pool (tenant_id, id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES common_user.common_user (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS flowboard.routing_task_state (
    tenant_id UUID NOT NULL, task_id UUID NOT NULL,
    pool_key VARCHAR(30) NOT NULL CHECK (pool_key = 'ORDER_COVER'),
    evaluated_pool_revision BIGINT, evaluated_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, task_id),
    FOREIGN KEY (tenant_id, task_id) REFERENCES flowboard.task (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS flowboard.routing_failure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    task_id UUID NOT NULL, pool_key VARCHAR(30) NOT NULL CHECK (pool_key = 'ORDER_COVER'),
    reason_code VARCHAR(40) NOT NULL CHECK (reason_code IN ('NO_POOL','POOL_EMPTY','NO_VALID_RECIPIENT','RECIPIENT_INVALID')),
    user_id UUID, pool_revision BIGINT, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    CHECK ((reason_code = 'RECIPIENT_INVALID') = (user_id IS NOT NULL)),
    CHECK ((reason_code = 'NO_POOL') = (pool_revision IS NULL)),
    FOREIGN KEY (tenant_id, task_id) REFERENCES flowboard.routing_task_state (tenant_id, task_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES common_user.common_user (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_routing_failure_task ON flowboard.routing_failure (tenant_id, task_id);
CREATE TABLE IF NOT EXISTS flowboard.routing_failure_resolution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    failure_id UUID NOT NULL, resolved_at TIMESTAMPTZ NOT NULL DEFAULT now(), reason VARCHAR(100) NOT NULL,
    UNIQUE (tenant_id, failure_id),
    FOREIGN KEY (tenant_id, failure_id) REFERENCES flowboard.routing_failure (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS flowboard.routing_failure_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    failure_id UUID NOT NULL, recipient_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP' CHECK (channel = 'IN_APP'),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DELIVERED','FAILED','CANCELLED')),
    cancel_reason VARCHAR(40) CHECK (cancel_reason IN ('FAILURE_RESOLVED','RECIPIENT_NOT_ELIGIBLE')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error VARCHAR(500), delivered_at TIMESTAMPTZ,
    UNIQUE (tenant_id, failure_id, recipient_id, channel),
    FOREIGN KEY (tenant_id, failure_id) REFERENCES flowboard.routing_failure (tenant_id, id),
    FOREIGN KEY (tenant_id, recipient_id) REFERENCES common_user.common_user (tenant_id, id)
);
ALTER TABLE notification.notification_log ADD COLUMN delivery_key UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_delivery_key ON notification.notification_log (tenant_id, delivery_key)
    WHERE delivery_key IS NOT NULL;

ALTER TABLE flowboard.routing_pool ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_pool FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_pool FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE flowboard.routing_pool_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_pool_member FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_pool_member FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE flowboard.routing_task_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_task_state FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_task_state FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE flowboard.routing_failure ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_failure FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_failure FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE flowboard.routing_failure_resolution ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_failure_resolution FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_failure_resolution FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE flowboard.routing_failure_alert ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.routing_failure_alert FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.routing_failure_alert FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

CREATE FUNCTION flowboard.reject_routing_ledger_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper)
        OR current_user = 'fabric_system'
        OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system'
                   AND pg_has_role(current_user, oid, 'MEMBER')) INTO trusted_purge_role;
    IF TG_OP = 'DELETE' AND trusted_purge_role
        AND current_setting('app.routing_purge_tenant', true) = OLD.tenant_id::TEXT THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Routing ledger is append-only' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER routing_failure_immutable BEFORE UPDATE OR DELETE ON flowboard.routing_failure
    FOR EACH ROW EXECUTE FUNCTION flowboard.reject_routing_ledger_mutation();
CREATE TRIGGER routing_resolution_immutable BEFORE UPDATE OR DELETE ON flowboard.routing_failure_resolution
    FOR EACH ROW EXECUTE FUNCTION flowboard.reject_routing_ledger_mutation();
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON flowboard.routing_pool, flowboard.routing_pool_member TO fabric_app;
        GRANT SELECT, INSERT, UPDATE ON flowboard.routing_task_state, flowboard.routing_failure_alert TO fabric_app;
        GRANT SELECT, INSERT ON flowboard.routing_failure, flowboard.routing_failure_resolution TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON flowboard.routing_pool, flowboard.routing_pool_member,
            flowboard.routing_task_state, flowboard.routing_failure_alert TO fabric_system;
        GRANT SELECT, INSERT, DELETE ON flowboard.routing_failure, flowboard.routing_failure_resolution TO fabric_system;
    END IF;
END $$;

-- The existing notification mechanism seeds template and operational tenants; onboarding clones these.
INSERT INTO i18n.translation_key (id, tenant_id, uid, key_code, module, default_value, description)
SELECT gen_random_uuid(), t.id, gen_random_uuid()::text, k.code, 'NOTIFICATION', k.value, k.value
FROM common_tenant.common_tenant t
CROSS JOIN (VALUES
    ('notification.routing_failure.title', 'Routing requires attention'),
    ('notification.routing_failure.body', 'Task {taskId} has routing failure {failureId}.')
) k(code, value)
WHERE t.type <> 'SYSTEM'
ON CONFLICT (tenant_id, key_code) DO NOTHING;
INSERT INTO i18n.translation_value (id, tenant_id, translation_key_id, locale, value, is_override)
SELECT gen_random_uuid(), tenant_id, id, 'TR',
    CASE key_code WHEN 'notification.routing_failure.title' THEN 'Yönlendirme müdahale gerektiriyor'
    ELSE '{taskId} görevinin yönlendirme hatası: {failureId}.' END, false
FROM i18n.translation_key WHERE key_code IN ('notification.routing_failure.title','notification.routing_failure.body')
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;
INSERT INTO notification.notification_template
    (id, tenant_id, uid, event_type, channel, title_key, body_key, importance, delivery_type)
SELECT gen_random_uuid(), id, gen_random_uuid()::text, 'ROUTING_FAILURE', 'IN_APP',
    'notification.routing_failure.title', 'notification.routing_failure.body', 'CRITICAL', 'INSTANT'
FROM common_tenant.common_tenant WHERE type <> 'SYSTEM'
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;
