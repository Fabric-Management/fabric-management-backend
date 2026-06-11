-- ============================================================================
-- V20260611050200: RLS shared-read carve-out for fiber ecosystem tables
-- ============================================================================
-- Replaces the strict single-tenant FOR ALL policy with:
--   SELECT: tenant's own data + TEMPLATE tenant's canonical shared data
--   INSERT/UPDATE/DELETE: strictly tenant's own data only
--
-- Tables affected:
--   1. prod_fiber + prod_product — canonical platform fibers (PF4)
--   2. prod_fiber_iso_code, prod_fiber_category, prod_fiber_certification
--      — FK-referenced by shared fibers; carve-out needed for JPA FK resolution
-- ============================================================================

-- ── prod_fiber ──────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber;

-- READ: tenant's own + template tenant's shared canonical fibers
CREATE POLICY rls_tenant_read ON production.prod_fiber
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant', true)::uuid
        OR tenant_id = '00000000-0000-0000-ffff-000000000001'
    );

-- WRITE (INSERT): only tenant's own
CREATE POLICY rls_tenant_insert ON production.prod_fiber
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- WRITE (UPDATE): only tenant's own
CREATE POLICY rls_tenant_update ON production.prod_fiber
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- WRITE (DELETE): only tenant's own
CREATE POLICY rls_tenant_delete ON production.prod_fiber
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);


-- ── prod_product ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_product;

-- READ: tenant's own + template tenant's shared canonical products
CREATE POLICY rls_tenant_read ON production.prod_product
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant', true)::uuid
        OR tenant_id = '00000000-0000-0000-ffff-000000000001'
    );

-- WRITE (INSERT): only tenant's own
CREATE POLICY rls_tenant_insert ON production.prod_product
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- WRITE (UPDATE): only tenant's own
CREATE POLICY rls_tenant_update ON production.prod_product
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- WRITE (DELETE): only tenant's own
CREATE POLICY rls_tenant_delete ON production.prod_product
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);


-- ============================================================================
-- FK-referenced reference tables: SELECT carve-out required
-- ============================================================================
-- Shared fibers (TEMPLATE tenant) hold FK references to TEMPLATE's
-- prod_fiber_iso_code, prod_fiber_category, and prod_fiber_certification.
-- Without SELECT carve-out, JPA cannot resolve these FKs for other tenants
-- and throws EntityNotFoundException.
--
-- These tables are also clone-on-create (TenantClonerService copies them
-- into each new tenant). The service layer handles deduplication by
-- querying with explicit tenant_id filters for listing purposes.
-- The carve-out is ONLY needed for FK resolution from shared fibers.
-- ============================================================================

-- ── prod_fiber_iso_code ─────────────────────────────────────────────────────

DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_iso_code;

CREATE POLICY rls_tenant_read ON production.prod_fiber_iso_code
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant', true)::uuid
        OR tenant_id = '00000000-0000-0000-ffff-000000000001'
    );

CREATE POLICY rls_tenant_insert ON production.prod_fiber_iso_code
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_update ON production.prod_fiber_iso_code
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_delete ON production.prod_fiber_iso_code
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);


-- ── prod_fiber_category ─────────────────────────────────────────────────────

DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_category;

CREATE POLICY rls_tenant_read ON production.prod_fiber_category
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant', true)::uuid
        OR tenant_id = '00000000-0000-0000-ffff-000000000001'
    );

CREATE POLICY rls_tenant_insert ON production.prod_fiber_category
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_update ON production.prod_fiber_category
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_delete ON production.prod_fiber_category
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);


-- ── prod_fiber_certification ────────────────────────────────────────────────

DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_certification;

CREATE POLICY rls_tenant_read ON production.prod_fiber_certification
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant', true)::uuid
        OR tenant_id = '00000000-0000-0000-ffff-000000000001'
    );

CREATE POLICY rls_tenant_insert ON production.prod_fiber_certification
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_update ON production.prod_fiber_certification
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_delete ON production.prod_fiber_certification
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
