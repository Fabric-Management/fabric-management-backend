-- T0: Fix session variable inconsistency (app.tenant_id → app.current_tenant)
-- Bu migration mevcut 3 kırık RLS politikasını standardize eder.
-- Sonraki T3 migration'ı tüm tablolara politika ekleyecek.

-- 1. Drop eski tutarsız politikalar
DROP POLICY IF EXISTS inheritance_rule_schema_rls 
    ON production.inheritance_rule_schema;
DROP POLICY IF EXISTS tenant_isolation_wo_consumption 
    ON production.work_order_consumption;
DROP POLICY IF EXISTS tenant_isolation_wo_output 
    ON production.work_order_output;

-- 2. Standart politikalarla yeniden oluştur (USING + WITH CHECK)
CREATE POLICY rls_tenant_isolation ON production.inheritance_rule_schema
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_isolation ON production.work_order_consumption
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY rls_tenant_isolation ON production.work_order_output
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 3. FORCE RLS ekle (henüz app rolü olmasa da, T1'e hazırlık)
ALTER TABLE production.inheritance_rule_schema FORCE ROW LEVEL SECURITY;
ALTER TABLE production.work_order_consumption FORCE ROW LEVEL SECURITY;
ALTER TABLE production.work_order_output FORCE ROW LEVEL SECURITY;
