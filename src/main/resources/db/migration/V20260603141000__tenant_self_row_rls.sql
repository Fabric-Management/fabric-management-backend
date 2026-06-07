-- T4: Self-row RLS policy for the tenant root table.
-- Ensures fabric_app can only see its own tenant metadata (id = current_setting).
-- Tenant creation and cross-tenant queries go through fabric_system (BYPASSRLS).

ALTER TABLE common_tenant.common_tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_tenant.common_tenant FORCE ROW LEVEL SECURITY;

-- Self-row policy: tenant sees only its own row
-- NULL/empty current_tenant → no rows (fail-closed)
CREATE POLICY rls_tenant_self_row ON common_tenant.common_tenant
    FOR ALL
    USING (id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (id = current_setting('app.current_tenant', true)::uuid);
