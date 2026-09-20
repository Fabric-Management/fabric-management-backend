SET LOCAL lock_timeout = '5s';

CREATE SEQUENCE sales_ord.sales_order_creation_seq AS BIGINT CACHE 1 NO CYCLE;
-- Runtime roles are provisioned outside Flyway and are absent in some environments
-- (integration-test containers); every role grant in this script is therefore guarded.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT USAGE, SELECT ON SEQUENCE sales_ord.sales_order_creation_seq TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT USAGE, SELECT ON SEQUENCE sales_ord.sales_order_creation_seq TO fabric_system;
    END IF;
END $$;

ALTER TABLE sales_ord.sales_order ADD COLUMN creation_seq BIGINT;
ALTER TABLE sales_ord.sales_order ADD COLUMN cover_regime VARCHAR(20);
ALTER TABLE sales_ord.sales_order ADD CONSTRAINT chk_sales_order_cover_regime
    CHECK (cover_regime IS NULL OR cover_regime IN ('LEGACY','GOVERNED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_order_cover_identity ON sales_ord.sales_order (tenant_id, id);

CREATE FUNCTION sales_ord.assign_sales_order_creation_seq() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock_shared(
    (('x'||substr(md5(NEW.tenant_id::text||':ORDER_COVER_ACTIVATION'),1,16))::bit(64)::bigint));
  NEW.creation_seq=nextval('sales_ord.sales_order_creation_seq');
  RETURN NEW;
END $$;
CREATE TRIGGER sales_order_creation_boundary
  BEFORE INSERT ON sales_ord.sales_order FOR EACH ROW
  EXECUTE FUNCTION sales_ord.assign_sales_order_creation_seq();

CREATE FUNCTION sales_ord.reject_cover_regime_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.cover_regime IS NOT NULL AND NEW.cover_regime IS DISTINCT FROM OLD.cover_regime THEN
    RAISE EXCEPTION 'Sales-order cover regime is immutable' USING ERRCODE='55000';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER sales_order_cover_regime_immutable
  BEFORE UPDATE OF cover_regime ON sales_ord.sales_order FOR EACH ROW
  EXECUTE FUNCTION sales_ord.reject_cover_regime_change();

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_activation (
    tenant_id UUID PRIMARY KEY,
    id UUID NOT NULL UNIQUE,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    boundary_seq BIGINT NOT NULL CHECK (boundary_seq >= 0),
    activated_at TIMESTAMPTZ NOT NULL,
    activated_by UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_case (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    sales_order_id UUID NOT NULL, revision BIGINT NOT NULL CHECK (revision > 0),
    state VARCHAR(30) NOT NULL CHECK (state IN ('OPEN','PARTIALLY_SETTLED','SETTLED','CANCELLED')),
    task_id UUID, closed_at TIMESTAMPTZ,
    CONSTRAINT uq_order_cover_case_order UNIQUE (tenant_id, sales_order_id),
    CONSTRAINT uq_order_cover_case_identity UNIQUE (tenant_id, id),
    CONSTRAINT uq_order_cover_case_subject UNIQUE (tenant_id,id,sales_order_id),
    CONSTRAINT fk_order_cover_case_order FOREIGN KEY (tenant_id,sales_order_id)
        REFERENCES sales_ord.sales_order(tenant_id,id)
);

-- Existing pre-route evidence is retained as a cancelled archival case. It does not enrol the
-- order, create a task, or change the order's regime.
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM sales_ord.order_cover_evidence_stream
      GROUP BY tenant_id,sales_order_id HAVING count(*) > 1) THEN
    RAISE EXCEPTION
      'Cannot bind multiple legacy evidence cases to one sales order without explicit remediation';
  END IF;
END $$;
INSERT INTO sales_ord.order_cover_case
    (id,tenant_id,uid,created_at,created_by,updated_at,updated_by,is_active,version,
     sales_order_id,revision,state,closed_at)
SELECT s.case_id,s.tenant_id,'SYS-000-OCC-'||replace(s.case_id::text,'-',''),
       s.created_at,s.created_by,s.updated_at,s.updated_by,true,0,s.sales_order_id,1,'CANCELLED',s.created_at
FROM sales_ord.order_cover_evidence_stream s
ON CONFLICT (tenant_id,sales_order_id) DO NOTHING;

ALTER TABLE sales_ord.order_cover_evidence_stream ADD CONSTRAINT fk_cover_stream_real_case
    FOREIGN KEY (tenant_id,case_id,sales_order_id)
    REFERENCES sales_ord.order_cover_case(tenant_id,id,sales_order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_cover_evidence_tenant_id
  ON sales_ord.order_cover_evidence(tenant_id,id);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_case_line (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL, sales_order_line_id UUID NOT NULL,
    settled_at TIMESTAMPTZ, result_id UUID,
    CONSTRAINT uq_order_cover_case_line UNIQUE(tenant_id,case_id,sales_order_line_id),
    CONSTRAINT fk_order_cover_line_case FOREIGN KEY(tenant_id,case_id)
        REFERENCES sales_ord.order_cover_case(tenant_id,id),
    CONSTRAINT fk_order_cover_line_order_line FOREIGN KEY(tenant_id,sales_order_line_id)
        REFERENCES sales_ord.sales_order_line(tenant_id,id)
);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_result (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL, case_revision BIGINT NOT NULL, sales_order_id UUID NOT NULL,
    actor_id UUID NOT NULL, actor_kind VARCHAR(20) NOT NULL CHECK(actor_kind IN ('USER','SYSTEM')),
    policy_key VARCHAR(100), rationale VARCHAR(1000), note VARCHAR(1000),
    recorded_at TIMESTAMPTZ NOT NULL, evidence_id UUID NOT NULL, evidence_revision BIGINT NOT NULL,
    supersedes_result_id UUID,
    CONSTRAINT uq_order_cover_result_identity UNIQUE(tenant_id,id),
    CONSTRAINT fk_order_cover_result_case FOREIGN KEY(tenant_id,case_id,sales_order_id)
        REFERENCES sales_ord.order_cover_case(tenant_id,id,sales_order_id),
    CONSTRAINT fk_order_cover_result_evidence FOREIGN KEY(tenant_id,evidence_id)
        REFERENCES sales_ord.order_cover_evidence(tenant_id,id)
);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_line_result (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    result_id UUID NOT NULL, line_id UUID NOT NULL, outcome VARCHAR(30) NOT NULL,
    quantity NUMERIC(15,3) NOT NULL CHECK(quantity > 0), unit VARCHAR(20) NOT NULL,
    suitability_at_decision VARCHAR(20) NOT NULL,
    requirement_profile_id UUID NOT NULL, requirement_profile_version INTEGER NOT NULL,
    evidence_sources JSONB NOT NULL CHECK(jsonb_typeof(evidence_sources)='array'),
    work_order_id UUID NOT NULL,
    CONSTRAINT uq_order_cover_line_settlement UNIQUE(tenant_id,line_id),
    CONSTRAINT fk_order_cover_line_result_result FOREIGN KEY(tenant_id,result_id)
        REFERENCES sales_ord.order_cover_result(tenant_id,id),
    CONSTRAINT fk_order_cover_line_result_line FOREIGN KEY(tenant_id,line_id)
        REFERENCES sales_ord.sales_order_line(tenant_id,id),
    CONSTRAINT fk_order_cover_line_result_profile
        FOREIGN KEY(tenant_id,requirement_profile_id,requirement_profile_version)
        REFERENCES sales_ord.requirement_profile_version(tenant_id,profile_id,profile_version),
    CONSTRAINT chk_order_cover_manual_outcome CHECK(outcome='MAKE_TO_ORDER')
);

ALTER TABLE sales_ord.order_cover_case_line ADD CONSTRAINT fk_order_cover_case_line_result
  FOREIGN KEY(tenant_id,result_id) REFERENCES sales_ord.order_cover_result(tenant_id,id);

CREATE INDEX IF NOT EXISTS idx_order_cover_case_state ON sales_ord.order_cover_case(tenant_id,state);
CREATE INDEX IF NOT EXISTS idx_order_cover_result_case ON sales_ord.order_cover_result(tenant_id,case_id,recorded_at);

ALTER TABLE sales_ord.order_cover_activation ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_activation FORCE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_case FORCE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_case_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_case_line FORCE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_result FORCE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_line_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_line_result FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_activation FOR ALL
  USING(tenant_id=current_setting('app.current_tenant',true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant',true)::uuid);
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_case FOR ALL
  USING(tenant_id=current_setting('app.current_tenant',true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant',true)::uuid);
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_case_line FOR ALL
  USING(tenant_id=current_setting('app.current_tenant',true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant',true)::uuid);
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_result FOR ALL
  USING(tenant_id=current_setting('app.current_tenant',true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant',true)::uuid);
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_line_result FOR ALL
  USING(tenant_id=current_setting('app.current_tenant',true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant',true)::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT ON sales_ord.order_cover_activation TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.order_cover_activation FROM fabric_app;
        GRANT SELECT, INSERT, UPDATE ON sales_ord.order_cover_case, sales_ord.order_cover_case_line TO fabric_app;
        REVOKE DELETE ON sales_ord.order_cover_case, sales_ord.order_cover_case_line FROM fabric_app;
        GRANT SELECT, INSERT ON sales_ord.order_cover_result, sales_ord.order_cover_line_result TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.order_cover_result, sales_ord.order_cover_line_result FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, DELETE ON sales_ord.order_cover_activation, sales_ord.order_cover_result,
            sales_ord.order_cover_line_result TO fabric_system;
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.order_cover_case, sales_ord.order_cover_case_line TO fabric_system;
        REVOKE UPDATE ON sales_ord.order_cover_result, sales_ord.order_cover_line_result FROM fabric_system;
    END IF;
END $$;

CREATE FUNCTION sales_ord.reject_order_cover_receipt_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role BOOLEAN;
BEGIN
  SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolsuper)
    OR current_user='fabric_system'
    OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fabric_system' AND pg_has_role(current_user,oid,'MEMBER'))
  INTO trusted_purge_role;
  IF TG_OP='DELETE' AND trusted_purge_role
    AND current_setting('app.order_cover_result_purge_tenant',true)=OLD.tenant_id::text THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'Order-cover receipts are immutable' USING ERRCODE='55000';
END $$;
CREATE TRIGGER order_cover_result_immutable BEFORE UPDATE OR DELETE ON sales_ord.order_cover_result
  FOR EACH ROW EXECUTE FUNCTION sales_ord.reject_order_cover_receipt_mutation();
CREATE TRIGGER order_cover_line_result_immutable BEFORE UPDATE OR DELETE ON sales_ord.order_cover_line_result
  FOR EACH ROW EXECUTE FUNCTION sales_ord.reject_order_cover_receipt_mutation();
